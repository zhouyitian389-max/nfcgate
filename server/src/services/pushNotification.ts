import prisma from '../db.js';
import { createSign } from 'crypto';
import { readFileSync } from 'fs';

interface ServiceAccountKey {
  project_id: string;
  client_email: string;
  private_key: string;
}

// OAuth2 token cache
let cachedToken: string | null = null;
let tokenExpiresAt = 0;

function loadServiceAccount(): ServiceAccountKey | null {
  const jsonEnv = process.env.FCM_SERVICE_ACCOUNT_JSON;
  if (jsonEnv) {
    try {
      return JSON.parse(jsonEnv) as ServiceAccountKey;
    } catch {
      console.error(JSON.stringify({ type: 'FCM_ERROR', reason: 'Failed to parse FCM_SERVICE_ACCOUNT_JSON' }));
      return null;
    }
  }
  const credFile = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (credFile) {
    try {
      return JSON.parse(readFileSync(credFile, 'utf8')) as ServiceAccountKey;
    } catch {
      console.error(JSON.stringify({ type: 'FCM_ERROR', reason: 'Failed to read GOOGLE_APPLICATION_CREDENTIALS file' }));
      return null;
    }
  }
  return null;
}

async function getOAuth2Token(): Promise<string | null> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && now < tokenExpiresAt) {
    return cachedToken;
  }

  const sa = loadServiceAccount();
  if (!sa) return null;

  // Build JWT for Google OAuth2
  const iat = now;
  const exp = iat + 3600;
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat,
    exp
  })).toString('base64url');

  const signingInput = header + '.' + payload;
  const sign = createSign('RSA-SHA256');
  sign.update(signingInput);
  const signature = sign.sign(sa.private_key, 'base64url');
  const jwt = signingInput + '.' + signature;

  // Exchange JWT for access token
  const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth2:grant-type:jwt-bearer',
      assertion: jwt
    }).toString()
  });

  if (!tokenRes.ok) {
    const errText = await tokenRes.text();
    console.error(JSON.stringify({ type: 'FCM_ERROR', reason: 'OAuth2 token exchange failed', detail: errText }));
    return null;
  }

  const tokenData = await tokenRes.json() as { access_token: string; expires_in: number };
  cachedToken = tokenData.access_token;
  // Cache until 5 minutes before expiry
  tokenExpiresAt = now + tokenData.expires_in - 300;
  return cachedToken;
}

async function sendFcmMessage(fcmToken: string, title: string, body: string, data?: Record<string, string>) {
  // Legacy API fallback (deprecated)
  const serverKey = process.env.FCM_SERVER_KEY;
  if (serverKey) {
    console.warn(JSON.stringify({ type: 'FCM_WARN', reason: 'FCM_SERVER_KEY (Legacy HTTP API) is deprecated. Migrate to FCM v1 API via FCM_PROJECT_ID and FCM_SERVICE_ACCOUNT_JSON.' }));
    try {
      const response = await fetch('https://fcm.googleapis.com/fcm/send', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'key=' + serverKey
        },
        body: JSON.stringify({
          to: fcmToken,
          notification: { title, body },
          data: data || {}
        })
      });
      const result = await response.json() as { success?: number };
      console.info(JSON.stringify({ type: 'FCM_SENT', api: 'legacy', token: fcmToken.slice(0, 8) + '...', success: result.success }));
      return result.success === 1;
    } catch (error) {
      console.error(JSON.stringify({ type: 'FCM_ERROR', api: 'legacy', error: error instanceof Error ? error.message : 'Unknown' }));
      return false;
    }
  }

  // FCM v1 API
  const projectId = process.env.FCM_PROJECT_ID;
  if (!projectId) {
    console.info(JSON.stringify({ type: 'FCM_SKIP', reason: 'FCM_PROJECT_ID not configured', title }));
    return false;
  }

  const accessToken = await getOAuth2Token();
  if (!accessToken) {
    console.info(JSON.stringify({ type: 'FCM_SKIP', reason: 'Could not obtain OAuth2 access token', title }));
    return false;
  }

  try {
    const endpoint = 'https://fcm.googleapis.com/v1/projects/' + projectId + '/messages:send';
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + accessToken
      },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          notification: { title, body },
          data: data || {}
        }
      })
    });

    if (!response.ok) {
      const errText = await response.text();
      console.error(JSON.stringify({ type: 'FCM_ERROR', api: 'v1', status: response.status, detail: errText }));
      return false;
    }

    console.info(JSON.stringify({ type: 'FCM_SENT', api: 'v1', token: fcmToken.slice(0, 8) + '...' }));
    return true;
  } catch (error) {
    console.error(JSON.stringify({ type: 'FCM_ERROR', api: 'v1', error: error instanceof Error ? error.message : 'Unknown' }));
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
  const body = 'Session ' + sessionId.slice(0, 8) + '...';
  await notifyAccountDevices(accountId, title, body, { event, sessionId });
}
