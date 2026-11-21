const consentKey = "cena.preference-storage";
const keys = ["cena-theme", "cenaaberta.rating-control"] as const;
type PreferenceKey = typeof keys[number];

export function allowsPreferences() {
  try { return localStorage.getItem(consentKey) !== "off"; }
  catch { return false; }
}

export function readPreference(key: PreferenceKey) {
  try { return (allowsPreferences() ? localStorage : sessionStorage).getItem(key); }
  catch { return null; }
}

export function writePreference(key: PreferenceKey, value: string) {
  try { (allowsPreferences() ? localStorage : sessionStorage).setItem(key, value); }
  catch { /* Storage can be disabled by the browser. */ }
}

export function allowPreferences(allow: boolean) {
  const previous = keys.map(key => readPreference(key));
  localStorage.setItem(consentKey, allow ? "on" : "off");
  keys.forEach((key, index) => {
    (allow ? sessionStorage : localStorage).removeItem(key);
    if (previous[index] !== null) writePreference(key, previous[index]);
  });
}
