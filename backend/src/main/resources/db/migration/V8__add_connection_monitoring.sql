-- Module 8: Settings — Tab 2 (Database Settings)
ALTER TABLE database_connections ADD COLUMN monitoring_enabled BOOLEAN NOT NULL DEFAULT TRUE;
