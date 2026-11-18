"""Operations for the invited alpha. Requires Python 3 and Docker Compose."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
from datetime import datetime, timezone
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ["docker", "compose", "--project-directory", str(ROOT), "--env-file",
           str(ROOT / "deploy/.env"), "-f", str(ROOT / "compose.yaml"),
           "-f", str(ROOT / "compose.alpha.yaml")]


def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def configuration():
    result = run(COMPOSE + ["config", "--format", "json"], capture_output=True, text=True)
    config = json.loads(result.stdout)
    services = config["services"]
    social = services["social"]["environment"]
    web = services["web"]["environment"]
    url = urlparse(web["NEXTAUTH_URL"])
    if url.scheme != "https" or not url.hostname or url.username or url.password or url.query or url.fragment or url.path not in ("", "/"):
        raise ValueError("ALPHA_URL deve ser uma origem HTTPS sem caminho ou credenciais.")
    if social["FRONTEND_URL"] != web["NEXTAUTH_URL"]:
        raise ValueError("URLs de login e e-mail divergem.")
    if social["INVITE_ONLY"] != "true" or not social["INVITED_EMAILS"].strip(" ,") or social["EMAIL_MODE"] != "smtp":
        raise ValueError("Convites e SMTP devem estar habilitados.")
    values = [web["NEXTAUTH_SECRET"], social["INTERNAL_SECRET"], social["CATALOG_INTERNAL_SECRET"],
              services["catalog-db"]["environment"]["POSTGRES_PASSWORD"], services["social-db"]["environment"]["POSTGRES_PASSWORD"]]
    if any(len(value) < 32 for value in values) or len(set(values)) != len(values):
        raise ValueError("Use cinco segredos distintos com pelo menos 32 caracteres.")
    for name, service in services.items():
        for port in service.get("ports", []):
            if name != "web" or port.get("host_ip") != "127.0.0.1" or str(port.get("published")) != "3000":
                raise ValueError("Somente o frontend em loopback pode publicar portas.")
    return config


def backup():
    configuration()
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + secrets.token_hex(3)
    destination = ROOT / "backups" / stamp
    destination.mkdir(parents=True, mode=0o700)
    active = run(COMPOSE + ["ps", "--status", "running", "--services"], capture_output=True, text=True).stdout.split()
    if not {"catalog-db", "social-db"}.issubset(active):
        raise ValueError("Os dois bancos devem estar em execuÃ§Ã£o.")
    writers = [name for name in ("web", "social", "catalog") if name in active]
    try:
        if writers:
            run(COMPOSE + ["stop", *writers])
        checksums = {}
        for name in ("catalog", "social"):
            target = destination / (name + ".dump")
            with target.open("wb") as output:
                run(COMPOSE + ["exec", "-T", name + "-db", "pg_dump", "-U", name, "-d", name, "-Fc", "--no-owner", "--no-acl"], stdout=output)
            checksums[target.name] = hashlib.sha256(target.read_bytes()).hexdigest()
        commit = run(["git", "-C", str(ROOT), "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
        (destination / "manifest.json").write_text(json.dumps({"commit": commit, "sha256": checksums}, indent=2) + "\n")
        print("Backup concluÃ­do:", destination)
        print("Copie para armazenamento privado fora da VM e execute restore-check.")
    finally:
        if writers:
            run(COMPOSE + ["start", *writers])


def restore_check(directory):
    directory = Path(directory).resolve()
    manifest = json.loads((directory / "manifest.json").read_text())
    for name in ("catalog", "social"):
        target = directory / (name + ".dump")
        if hashlib.sha256(target.read_bytes()).hexdigest() != manifest["sha256"][target.name]:
            raise ValueError("Checksum invÃ¡lido; restauraÃ§Ã£o cancelada.")
    container = "cena-restore-" + secrets.token_hex(8)
    environment = dict(os.environ, POSTGRES_PASSWORD=secrets.token_urlsafe(32))
    started = False
    try:
        run(["docker", "run", "-d", "--name", container, "--network", "none", "--tmpfs", "/var/lib/postgresql/data", "-e", "POSTGRES_PASSWORD", "postgres:16-alpine"], env=environment, stdout=subprocess.DEVNULL)
        started = True
        import time
        for attempt in range(60):
            ready = subprocess.run(["docker", "exec", container, "pg_isready", "-U", "postgres"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if ready.returncode == 0:
                break
            time.sleep(1)
        else:
            raise ValueError("Banco temporÃ¡rio nÃ£o iniciou.")
        for name in ("catalog", "social"):
            run(["docker", "exec", container, "createdb", "-U", "postgres", name])
            with (directory / (name + ".dump")).open("rb") as source:
                run(["docker", "exec", "-i", container, "pg_restore", "-U", "postgres", "-d", name, "--exit-on-error", "--no-owner", "--no-acl"], stdin=source)
            run(["docker", "exec", container, "psql", "-U", "postgres", "-d", name, "-v", "ON_ERROR_STOP=1", "-c", 'SELECT count(*) FROM flyway_schema_history;'], stdout=subprocess.DEVNULL)
        print("Dois bancos restaurados em ambiente isolado; bancos do alpha nÃ£o foram alterados.")
    finally:
        if started:
            run(["docker", "rm", "-f", "-v", container], stdout=subprocess.DEVNULL)


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["check", "up", "backup", "restore-check"])
    parser.add_argument("directory", nargs="?")
    args = parser.parse_args()
    if args.command == "restore-check":
        if not args.directory:
            parser.error("Informe a pasta de backup.")
        restore_check(args.directory)
    elif args.command == "backup":
        backup()
    else:
        configuration()
        print("ConfiguraÃ§Ã£o alpha validada; segredos nÃ£o exibidos.")
        if args.command == "up":
            run(COMPOSE + ["up", "-d", "--build", "--wait", "--wait-timeout", "240"])
            run(COMPOSE + ["ps"])
            print("Valide a URL HTTPS e os fluxos antes de compartilhar.")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError:
        sys.exit("OperaÃ§Ã£o externa falhou. Confira Docker, configuraÃ§Ã£o e serviÃ§os. Valores privados omitidos.")
    except (ValueError, OSError, KeyError) as error:
        sys.exit(str(error))
