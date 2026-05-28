import prisma from '../db.js';

const FCM_ENDPOINT = 'https://fcm.googleapis.com/fcm/send';

async function sendFcmMessage(fcmToken: string, title: string, body: string, data?: Record<string, string>) {
  const serverKey = process.env.FCM_SERVER_KEY;
  if (!serverKey) {
    console.info(JSON.stringify({ type: 'FCM_SKIP', reason: 'FCM_SERVER_KEY not configured', title }));
    return false;
  }

  try {
    const response = await fetch(FCM_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `key=${serverKey}`
      },
      body: JSON.stringify({
        to: fcmToken,
        notification: { title, body },
        data: data || {}
      })
    });
    const result = await response.json() as { success?: number };
    console.info(JSON.stringify({ type: 'FCM_SENT', token: fcmToken.slice(0, 8) + '...', success: result.success }));
    return result.success === 1;
  } catch (error) {
    console.error(JSON.stringify({ type: 'FCM_ERROR', error: error instanceof Error ? error.message : 'Unknown' }));
    return false;
  }
}

export async function notifyAccountDevices(accountId: string, title: string, body: string, data?: Record<string, string>) {
  const devices = await prisma.device.findMany({
    where: { accountId, fcmToken: { not: null }, online: true },
    select: { fcmToken: true }
  });

  const results = await Promise.allSettled(
    devices
      .filter((d): d is { fcmToken: string } => Boolean(d.fcmToken))
      .map((d) => sendFcmMessage(d.fcmToken, title, body, data))
  );

  return results.filter((r) => r.status === 'fulfilled' && r.value).length;
}

export async function notifyRelaySessionEvent(accountId: string, event: 'session_start' | 'session_end', sessionId: string) {
  const title = event === 'session_start' ? 'Relay Session Started' : 'Relay Session Ended';
  const body = `Session ${sessionId.slice(0, 8)}...`;
  await notifyAccountDevices(accountId, title, body, { event, sessionId });
}
