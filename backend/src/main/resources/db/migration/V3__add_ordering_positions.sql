-- Itens e histórico precisam preservar a ordem de inclusão; o SQLite não garante a ordem das linhas.
ALTER TABLE order_items ADD COLUMN position INTEGER NOT NULL DEFAULT 0;
ALTER TABLE order_status_history ADD COLUMN position INTEGER NOT NULL DEFAULT 0;
