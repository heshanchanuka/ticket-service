-- Migration V1: Create ticket schema tables

CREATE TABLE ticket_type (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    total_quantity INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE ticket_inventory (
    id UUID PRIMARY KEY,
    ticket_type_id UUID NOT NULL,
    available_quantity INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ticket_inventory_ticket_type_id UNIQUE (ticket_type_id),
    CONSTRAINT fk_ticket_inventory_ticket_type FOREIGN KEY (ticket_type_id) 
        REFERENCES ticket_type (id) ON DELETE RESTRICT
);

CREATE TABLE ticket_hold (
    id UUID PRIMARY KEY,
    ticket_type_id UUID NOT NULL,
    booking_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ticket_hold_ticket_type FOREIGN KEY (ticket_type_id) 
        REFERENCES ticket_type (id) ON DELETE RESTRICT
);

CREATE INDEX idx_ticket_hold_expires_at ON ticket_hold (expires_at);
CREATE INDEX idx_ticket_hold_booking_id ON ticket_hold (booking_id);
