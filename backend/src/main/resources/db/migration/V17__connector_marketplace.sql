CREATE TABLE connector_catalog (
    id VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT,
    icon VARCHAR(50),
    auth_type VARCHAR(50) DEFAULT 'API_KEY',
    config_schema JSONB NOT NULL DEFAULT '{}',
    is_available BOOLEAN DEFAULT TRUE
);
INSERT INTO connector_catalog VALUES
('salesforce','Salesforce','CRM','Connect to Salesforce CRM','cloud','OAUTH2','{"fields":["instance_url","client_id","client_secret"]}',true),
('hubspot','HubSpot','CRM','Connect to HubSpot CRM','target','API_KEY','{"fields":["api_key"]}',true),
('zendesk','Zendesk','HELPDESK','Connect to Zendesk Support','headphones','API_KEY','{"fields":["subdomain","api_key","email"]}',true),
('jira','Jira','PROJECT','Connect to Jira','clipboard','API_KEY','{"fields":["base_url","email","api_token"]}',true),
('stripe','Stripe','FINANCE','Connect to Stripe Payments','credit-card','API_KEY','{"fields":["api_key"]}',true),
('github','GitHub','DEV','Connect to GitHub','github','OAUTH2','{"fields":["access_token"]}',true),
('postgresql','PostgreSQL','DATABASE','Connect to PostgreSQL','database','BASIC','{"fields":["url","username","password","query"]}',true),
('mysql','MySQL','DATABASE','Connect to MySQL','database','BASIC','{"fields":["url","username","password","query"]}',true);
