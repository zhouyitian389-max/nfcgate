import { ServerResponse } from 'http';
import { randomUUID } from 'crypto';

interface SSEClient {
  id: string;
  userId: string;
  res: ServerResponse;
}

class SSEHub {
  private clients: Map<string, SSEClient> = new Map();
  private readonly maxConnectionsPerUser = 5;

  addClient(userId: string, res: ServerResponse): string {
    const userClients = [...this.clients.values()].filter((client) => client.userId === userId);
    while (userClients.length >= this.maxConnectionsPerUser) {
      const oldest = userClients.shift();
      if (!oldest) break;
      this.clients.delete(oldest.id);
      try {
        oldest.res.end();
      } catch {
        // ignore response close errors
      }
    }

    const id = randomUUID();
    this.clients.set(id, { id, userId, res });
    return id;
  }

  removeClient(id: string) {
    this.clients.delete(id);
  }

  sendToUser(userId: string, event: string, data: any) {
    for (const client of this.clients.values()) {
      if (client.userId === userId) {
        client.res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
      }
    }
  }

  broadcast(event: string, data: any) {
    for (const client of this.clients.values()) {
      client.res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
    }
  }
}

export const sseHub = new SSEHub();
