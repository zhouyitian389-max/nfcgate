import { ServerResponse } from 'http';
import { randomUUID } from 'crypto';

interface SSEClient {
  id: string;
  userId: string;
  res: ServerResponse;
}

class SSEHub {
  private clients: Map<string, SSEClient> = new Map();

  addClient(userId: string, res: ServerResponse): string {
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
        if (!this.write(client, event, data)) {
          this.clients.delete(client.id);
        }
      }
    }
  }

  broadcast(event: string, data: any) {
    for (const client of this.clients.values()) {
      if (!this.write(client, event, data)) {
        this.clients.delete(client.id);
      }
    }
  }

  private write(client: SSEClient, event: string, data: any) {
    if (client.res.destroyed || client.res.writableEnded) {
      return false;
    }

    try {
      client.res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
      return true;
    } catch {
      return false;
    }
  }
}

export const sseHub = new SSEHub();
