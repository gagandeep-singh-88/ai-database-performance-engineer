export type SslMode = 'DISABLE' | 'PREFER' | 'REQUIRE';
export type ConnectionStatus = 'UNKNOWN' | 'HEALTHY' | 'UNREACHABLE';

export interface ConnectionResponse {
  id: string;
  name: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  sslMode: SslMode;
  status: ConnectionStatus;
  monitoringEnabled: boolean;
  lastTestedAt: string | null;
  lastError: string | null;
  createdAt: string;
}

export interface ConnectionPayload {
  name: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  password: string;
  sslMode: SslMode;
}

/** Password is optional on edit: blank keeps the currently stored secret. */
export interface UpdateConnectionPayload {
  name: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  password: string;
  sslMode: SslMode;
}

export interface ConnectionTestResult {
  success: boolean;
  latencyMs: number;
  serverVersion: string | null;
  databaseSize: string | null;
  pgStatStatementsEnabled: boolean;
  readOnlyEnforced: boolean;
  message: string;
}
