import prisma from '../db.js';

const DEVICE_OFFLINE_THRESHOLD_MS = 5 * 60 * 1000;

async function markStaleDevicesOffline() {
  const threshold = new Date(Date.now() - DEVICE_OFFLINE_THRESHOLD_MS);
  const result = await prisma.device.updateMany({
    where: {
      online: true,
      lastSeen: { lt: threshold }
    },
    data: { online: false }
  });
  if (result.count > 0) {
    console.info(JSON.stringify({ type: 'DEVICE_MONITOR', offlined: result.count }));
  }
}

export function startDeviceMonitor() {
  const timer = setInterval(() => {
    void markStaleDevicesOffline().catch((err) => {
      console.error('[deviceMonitor] Error:', err instanceof Error ? err.message : 'Unknown');
    });
  }, 60_000);
  timer.unref();

  return () => clearInterval(timer);
}
