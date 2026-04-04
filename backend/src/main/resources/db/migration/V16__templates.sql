CREATE TABLE object_type_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    category VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    template_data JSONB NOT NULL,
    icon VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
INSERT INTO object_type_templates (category, name, display_name, description, icon, template_data)
VALUES
('CRM', 'crm-company', 'Company', 'Standard company/account entity', 'building', '{"properties":[{"apiName":"name","displayName":"Name","dataType":"STRING","isRequired":true},{"apiName":"industry","displayName":"Industry","dataType":"STRING"},{"apiName":"website","displayName":"Website","dataType":"STRING"},{"apiName":"revenue","displayName":"Revenue","dataType":"FLOAT"},{"apiName":"employees","displayName":"Employees","dataType":"INTEGER"}]}'),
('CRM', 'crm-contact', 'Contact', 'Standard contact/person entity', 'user', '{"properties":[{"apiName":"firstName","displayName":"First Name","dataType":"STRING","isRequired":true},{"apiName":"lastName","displayName":"Last Name","dataType":"STRING","isRequired":true},{"apiName":"email","displayName":"Email","dataType":"STRING"},{"apiName":"phone","displayName":"Phone","dataType":"STRING"},{"apiName":"position","displayName":"Position","dataType":"STRING"}]}'),
('HELPDESK', 'helpdesk-ticket', 'Ticket', 'Support ticket entity', 'ticket', '{"properties":[{"apiName":"subject","displayName":"Subject","dataType":"STRING","isRequired":true},{"apiName":"description","displayName":"Description","dataType":"STRING"},{"apiName":"priority","displayName":"Priority","dataType":"STRING"},{"apiName":"status","displayName":"Status","dataType":"STRING"},{"apiName":"assignee","displayName":"Assignee","dataType":"STRING"}]}'),
('ERP', 'erp-invoice', 'Invoice', 'Invoice/billing entity', 'receipt', '{"properties":[{"apiName":"invoiceNumber","displayName":"Invoice Number","dataType":"STRING","isPrimaryKey":true},{"apiName":"amount","displayName":"Amount","dataType":"FLOAT","isRequired":true},{"apiName":"currency","displayName":"Currency","dataType":"STRING"},{"apiName":"dueDate","displayName":"Due Date","dataType":"DATETIME"},{"apiName":"status","displayName":"Status","dataType":"STRING"}]}');
