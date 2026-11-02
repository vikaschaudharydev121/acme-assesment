-- Illustrative reference rates (1 unit of local currency = usd_rate USD). Rates change through a new
-- migration so every figure shown in insights can be traced back to a versioned rate.
INSERT INTO country (code, name, currency, usd_rate) VALUES
    ('US', 'United States',  'USD', 1.0),
    ('GB', 'United Kingdom', 'GBP', 1.27),
    ('DE', 'Germany',        'EUR', 1.08),
    ('IN', 'India',          'INR', 0.012),
    ('CA', 'Canada',         'CAD', 0.73),
    ('AU', 'Australia',      'AUD', 0.66),
    ('SG', 'Singapore',      'SGD', 0.74),
    ('BR', 'Brazil',         'BRL', 0.18);
