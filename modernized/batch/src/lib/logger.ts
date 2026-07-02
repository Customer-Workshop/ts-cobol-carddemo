// Console + file logging for batch jobs (replaces JCL SYSOUT/JES spool).
import * as fs from 'fs';
import * as path from 'path';

export const OUTPUT_DIR = path.resolve(__dirname, '..', '..', 'output');
const LOG_DIR = path.join(OUTPUT_DIR, 'logs');

export interface Logger {
  info(message: string): void;
  error(message: string): void;
  logFile: string;
}

export function createLogger(jobName: string): Logger {
  fs.mkdirSync(LOG_DIR, { recursive: true });
  const logFile = path.join(LOG_DIR, `${jobName}.log`);
  const write = (level: string, message: string): void => {
    const line = `${new Date().toISOString()} [${level}] ${jobName}: ${message}`;
    if (level === 'ERROR') {
      console.error(line);
    } else {
      console.log(line);
    }
    fs.appendFileSync(logFile, line + '\n');
  };
  return {
    info: (m) => write('INFO', m),
    error: (m) => write('ERROR', m),
    logFile,
  };
}
