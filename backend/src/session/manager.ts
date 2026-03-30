import type { WebSocket } from 'ws';
import type { SessionState } from '../types/messages.js';
import { logger } from '../utils/logger.js';

interface SessionManager {
  createSession(ws: WebSocket): string;
  getSession(sessionId: string): SessionState | null;
  updateSession(sessionId: string, updates: Partial<SessionState>): boolean;
  deleteSession(sessionId: string): boolean;
  getSessionByWebSocket(ws: WebSocket): SessionState | null;
  cleanupInactiveSessions(maxAgeMs: number): number;
}

class SessionStore implements SessionManager {
  private sessions: Map<string, SessionState>;
  private wsToSession: Map<WebSocket, string>;
  private readonly defaultMaxAge: number;

  constructor(defaultMaxAge: number = 30 * 60 * 1000) {
    this.sessions = new Map();
    this.wsToSession = new Map();
    this.defaultMaxAge = defaultMaxAge;
  }

  createSession(ws: WebSocket): string {
    const sessionId = this.generateSessionId();
    const now = Date.now();

    const session: SessionState = {
      sessionId,
      createdAt: now,
      lastActivity: now,
      status: 'idle',
      images: [],
    };

    this.sessions.set(sessionId, session);
    this.wsToSession.set(ws, sessionId);

    logger.info(sessionId, 'Session created', { createdAt: now });

    ws.on('close', () => {
      this.deleteSession(sessionId);
    });

    return sessionId;
  }

  getSession(sessionId: string): SessionState | null {
    return this.sessions.get(sessionId) || null;
  }

  updateSession(sessionId: string, updates: Partial<SessionState>): boolean {
    const session = this.sessions.get(sessionId);
    if (!session) return false;

    Object.assign(session, updates);
    session.lastActivity = Date.now();

    logger.debug(sessionId, 'Session updated', updates);
    return true;
  }

  deleteSession(sessionId: string): boolean {
    const session = this.sessions.get(sessionId);
    if (!session) return false;

    const ws = this.findWebSocketBySession(sessionId);
    if (ws) {
      this.wsToSession.delete(ws);
    }

    this.sessions.delete(sessionId);
    logger.info(sessionId, 'Session deleted');

    return true;
  }

  getSessionByWebSocket(ws: WebSocket): SessionState | null {
    const sessionId = this.wsToSession.get(ws);
    return sessionId ? this.getSession(sessionId) : null;
  }

  cleanupInactiveSessions(maxAgeMs: number = this.defaultMaxAge): number {
    const now = Date.now();
    let cleanedCount = 0;

    for (const [sessionId, session] of this.sessions.entries()) {
      if (now - session.lastActivity > maxAgeMs) {
        this.deleteSession(sessionId);
        cleanedCount++;
      }
    }

    if (cleanedCount > 0) {
      logger.info(undefined, `Cleaned up ${cleanedCount} inactive sessions`);
    }

    return cleanedCount;
  }

  private generateSessionId(): string {
    return `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
  }

  private findWebSocketBySession(sessionId: string): WebSocket | undefined {
    for (const [ws, id] of this.wsToSession.entries()) {
      if (id === sessionId) return ws;
    }
    return undefined;
  }
}

export const sessionManager = new SessionStore();
