// Service Worker básico para permitir la instalación (PWA)
self.addEventListener('install', (e) => {
  console.log('[Service Worker] Instalado');
});

self.addEventListener('fetch', (e) => {
  // Este bloque permite que la app funcione incluso con red inestable
  e.respondWith(fetch(e.request));
});
