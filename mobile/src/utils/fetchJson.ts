/**
 * GET a JSON document from a third-party service without using `fetch`.
 *
 * React Native's `fetch` polyfill (whatwg-fetch) builds its Response inside a timer and throws a
 * RangeError there when the server answers with a status outside 200-599 (e.g. Nominatim's 629
 * block response). That throw escapes every caller's try/catch and crashes the app. Using
 * XMLHttpRequest directly turns every outcome into a normal promise rejection instead.
 */
export function fetchJson<T = any>(
  url: string,
  headers: Record<string, string> = {},
  timeoutMs = 10000,
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('GET', url);
    xhr.timeout = timeoutMs;
    Object.entries(headers).forEach(([name, value]) => xhr.setRequestHeader(name, value));
    xhr.onload = () => {
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(new Error(`HTTP ${xhr.status} from ${url}`));
        return;
      }
      try {
        resolve(JSON.parse(xhr.responseText) as T);
      } catch {
        reject(new Error(`Invalid JSON from ${url}`));
      }
    };
    xhr.onerror = () => reject(new Error(`Network request failed: ${url}`));
    xhr.ontimeout = () => reject(new Error(`Request timed out: ${url}`));
    xhr.send();
  });
}
