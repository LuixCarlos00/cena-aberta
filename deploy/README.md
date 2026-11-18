# Alpha na Oracle com Tailscale Funnel

## Estado e requisitos

O Compose mantém dois PostgreSQL persistentes e publica somente o frontend em `127.0.0.1:3000`. Os serviços reiniciam com o Docker e os logs têm rotação. O comando `up` espera pela saúde do frontend e dos dois serviços. O cadastro por convite e o SMTP são obrigatórios neste ambiente.

A VM ainda precisa ser provisionada e validada na conta Oracle. Use Ubuntu ARM64, shape `VM.Standard.A1.Flex`, na região principal da conta, dentro da cota Always Free disponível. A documentação consultada em 18/11/2026 informa 2 OCPUs e 12 GB equivalentes. Confira o saldo já usado por outras VMs e volumes; não aceite recursos pagos como alternativa automática à falta de capacidade. VMs ociosas podem ser recolhidas.

Fontes: [Oracle Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm), [Docker no Ubuntu](https://docs.docker.com/engine/install/ubuntu/), [requisitos do Funnel](https://tailscale.com/docs/features/tailscale-funnel), [CLI Funnel](https://tailscale.com/docs/reference/tailscale-cli/funnel).

## Preparar o servidor

1. Crie a VM e guarde sua chave SSH fora do repositório. Restrinja a entrada SSH ao seu endereço de administração. Não abra 3000, 8181, 8182 ou 5432 na internet. Permita saída para atualizações, TMDB, SMTP e Tailscale.
2. Instale Git, Python 3, Docker Engine e o plugin Docker Compose pelo guia oficial para Ubuntu. Habilite o serviço Docker no boot. Use uma conta autorizada a acessar o Docker.
3. Instale o Tailscale na própria VM e autentique-a com `sudo tailscale up --hostname=cena-aberta-alpha`. Confirme MagicDNS, HTTPS e permissão `funnel` na política da tailnet. O login interativo deve ser feito pelo responsável pela conta.
4. Consulte o nome DNS com `tailscale status --json` (campo `Self.DNSName`, removendo o ponto final). A origem pública será `https://` seguida desse nome. Preserve o nome da máquina para manter os links.
5. Clone o repositório e crie a configuração privada:

```bash
git clone https://github.com/MaroTequi/cena-aberta.git
cd cena-aberta
umask 077
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
```

Preencha `deploy/.env` no servidor: `ALPHA_URL`, convidados, token TMDB, remetente Brevo verificado e credenciais SMTP. Gere cinco valores aleatórios distintos para os segredos internos, sessão e bancos, com pelo menos 32 caracteres; por exemplo `openssl rand -hex 32` para cada valor. Não cole segredos em issues, commits ou histórico de comandos. O modelo não contém credenciais.

## Subir e publicar

```bash
python3 deploy/alpha.py check
python3 deploy/alpha.py up
sudo tailscale funnel --bg 3000
tailscale funnel status
```

O comando `check` não exibe segredos. `up` compila para a arquitetura da VM e espera os serviços ficarem saudáveis. Não use o Compose base sozinho para o alpha: ele não força a restrição de cadastro. Qualquer pessoa pode abrir a página pública, mas novos cadastros exigem convite e confirmação de e-mail. Remover um convite não desativa uma conta existente.

Valide em rede externa, sem Tailscale instalado: login, convite autorizado, e-mail não autorizado, confirmação, recuperação e URLs recebidas por e-mail. Confirme o certificado reconhecido pelo navegador e o cookie seguro. Reinicie a VM e verifique a retomada. Só compartilhe após essa rodada. Para suspender o acesso público: `sudo tailscale funnel --https=443 off`.

## Backup e restauração de teste

```bash
python3 deploy/alpha.py backup
python3 deploy/alpha.py restore-check backups/PASTA_GERADA
```

O backup pausa temporariamente os serviços de aplicação para obter um par consistente dos bancos, gera dumps PostgreSQL, checksums e o commit de origem e retoma os serviços que estavam ativos. Isso exige uma janela curta de indisponibilidade. Não faça gravações externas nos bancos durante a operação. Só pastas com `manifest.json` são backups completos.

`restore-check` verifica checksums e restaura os dois bancos num PostgreSQL temporário sem rede e sem portas públicas, usando armazenamento em memória. Confere as tabelas de migração e remove apenas o contêiner temporário ao terminar. Não altera bancos de produção. Reserve memória suficiente para a cópia; para bases maiores, execute o ensaio em outro host Docker.

Copie cada backup concluído para armazenamento privado e criptografado **fora da VM**. Defina destino, retenção e frequência antes de convidar usuários; a transferência externa ainda não está automatizada. Os dumps contêm dados pessoais. Guarde separadamente a configuração privada, necessária para recuperar o ambiente. Não versione dumps ou credenciais.

Para recuperar após perda da VM, crie um ambiente separado com o commit do manifesto, configure os segredos, inicie apenas os dois bancos e restaure cada dump com `pg_restore --exit-on-error --no-owner --no-acl` em bancos vazios. Suba os serviços, valide contas e dados e só então aponte o Funnel para o ambiente recuperado. Não restaure sobre bancos em uso. O ensaio automático não substitui essa validação funcional de recuperação.

## Atualização

Faça backup e copie-o para fora antes de atualizar. Use `git pull --ff-only` e `python3 deploy/alpha.py up`. Confira saúde, migrações e fluxos. Para rollback com migração incompatível, recupere o par de bancos e o commit correspondente em ambiente separado; apenas trocar a imagem não reverte o banco. Nunca use `docker compose down -v` no ambiente real.

O GitHub Actions executa builds e testes da aplicação e um job ARM que compila o Compose, verifica saúde, faz backup e restaura em isolamento com dados descartáveis. SMTP real, TMDB real, acesso externo e disponibilidade da Oracle precisam ser validados no servidor.
