import { PrismaClient } from '@prisma/client';

const connectionLimit = Number(process.env.DATABASE_POOL_SIZE || 10);
const connectionTimeout = Number(process.env.DATABASE_CONNECT_TIMEOUT_MS || 5000);

const prisma = new PrismaClient({
  datasources: {
    db: {
      url: process.env.DATABASE_URL
    }
  },
  log: process.env.LOG_LEVEL === 'debug' ? ['query', 'info', 'warn', 'error'] : ['warn', 'error'],
  // connection_limit and connect_timeout are passed via the DATABASE_URL query string
  // but we expose the env vars for documentation purposes; append them here if not already set
  ...(() => {
    const url = process.env.DATABASE_URL || '';
    const sep = url.includes('?') ? '&' : '?';
    if (!url.includes('connection_limit') || !url.includes('connect_timeout')) {
      const extra = [
        !url.includes('connection_limit') ? `connection_limit=${connectionLimit}` : '',
        !url.includes('connect_timeout') ? `connect_timeout=${Math.ceil(connectionTimeout / 1000)}` : ''
      ].filter(Boolean).join('&');
      if (extra) {
        process.env.DATABASE_URL = `${url}${sep}${extra}`;
      }
    }
    return {};
  })()
});

export default prisma;
