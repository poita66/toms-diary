import { loadConfig } from '../config.js';

const config = loadConfig();

type LogLevel = 'debug' | 'info' | 'warn' | 'error';

interface LogContext {
  sessionId?: string;
  [key: string]: unknown;
}

interface LogEntry {
  timestamp: string;
  level: LogLevel;
  sessionId?: string;
  message: string;
  context?: Record<string, unknown>;
}

interface Timer {
  label: string;
  sessionId?: string;
  startTime: number;
  end(): void;
}

const logLevelOrder: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
};

const colors = {
  reset: '\x1b[0m',
  debug: '\x1b[36m',
  info: '\x1b[32m',
  warn: '\x1b[33m',
  error: '\x1b[31m',
  sessionId: '\x1b[35m',
};

function shouldLog(level: LogLevel): boolean {
  return logLevelOrder[level] >= logLevelOrder[config.logLevel as LogLevel];
}

function formatTimestamp(): string {
  return new Date().toISOString();
}

function formatLogEntry(entry: LogEntry, useColors: boolean): string {
  const { timestamp, level, sessionId, message, context } = entry;

  if (!useColors) {
    const jsonEntry: Record<string, unknown> = {
      timestamp,
      level,
      message,
      ...context,
    };
    if (sessionId) jsonEntry.sessionId = sessionId;
    return JSON.stringify(jsonEntry);
  }

  const color = colors[level] || colors.reset;
  const sessionStr = sessionId ? `${colors.sessionId}[${sessionId}]${colors.reset} ` : '';
  const contextStr = context && Object.keys(context).length > 0 ? ` ${JSON.stringify(context)}` : '';
  return `${color}[${timestamp}] [${level.toUpperCase()}]${colors.reset} ${sessionStr}${message}${contextStr}`;
}

function createTimer(label: string, sessionId?: string): Timer {
  const startTime = Date.now();

  return {
    label,
    sessionId,
    startTime,
    end(): void {
      const duration = Date.now() - startTime;
      logger.info(sessionId, `${label} completed in ${duration}ms`);
    },
  };
}

function log(
  level: LogLevel,
  sessionId: string | undefined,
  message: string,
  context?: Record<string, unknown>
): void {
  if (!shouldLog(level)) return;

  const entry: LogEntry = {
    timestamp: formatTimestamp(),
    level,
    message,
  };

  if (sessionId) entry.sessionId = sessionId;
  if (context && Object.keys(context).length > 0) entry.context = context;

  const useColors = process.env.NODE_ENV !== 'production';
  console.log(formatLogEntry(entry, useColors));
}

export const logger = {
  debug(sessionId: string | undefined, message: string, context?: Record<string, unknown>): void {
    log('debug', sessionId, message, context);
  },

  info(sessionId: string | undefined, message: string, context?: Record<string, unknown>): void {
    log('info', sessionId, message, context);
  },

  warn(sessionId: string | undefined, message: string, context?: Record<string, unknown>): void {
    log('warn', sessionId, message, context);
  },

  error(
    sessionId: string | undefined,
    message: string,
    error?: Error,
    context?: Record<string, unknown>
  ): void {
    const logContext = { ...context };
    if (error) {
      logContext.error = {
        message: error.message,
        stack: error.stack,
      };
    }
    log('error', sessionId, message, logContext);
  },

  startTimer(label: string, sessionId?: string): Timer {
    const timer = createTimer(label, sessionId);
    logger.debug(sessionId, `Started: ${label}`);
    return timer;
  },
};

export { type LogLevel, type LogContext, type Timer };
