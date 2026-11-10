-- Convert support.contact chat.hours / phone.hours from localized free-text
-- ({th,en,zh}) to structured ShopHour-like arrays (day 0=Monday .. 6=Sunday).
-- Legacy Thai/EN/ZH prose cannot be parsed reliably → always apply the defaults
-- below when the current value is not already a 7-day array.

-- Chat default: Mon–Sun 08:00–22:00
-- Phone default: Mon–Fri 08:00–18:00, Sat–Sun closed

UPDATE platform_settings
SET setting_value = jsonb_set(
  jsonb_set(
    setting_value,
    '{chat,hours}',
    '[
      {"day":0,"open":true,"start":"08:00","end":"22:00"},
      {"day":1,"open":true,"start":"08:00","end":"22:00"},
      {"day":2,"open":true,"start":"08:00","end":"22:00"},
      {"day":3,"open":true,"start":"08:00","end":"22:00"},
      {"day":4,"open":true,"start":"08:00","end":"22:00"},
      {"day":5,"open":true,"start":"08:00","end":"22:00"},
      {"day":6,"open":true,"start":"08:00","end":"22:00"}
    ]'::jsonb,
    true
  ),
  '{phone,hours}',
  '[
    {"day":0,"open":true,"start":"08:00","end":"18:00"},
    {"day":1,"open":true,"start":"08:00","end":"18:00"},
    {"day":2,"open":true,"start":"08:00","end":"18:00"},
    {"day":3,"open":true,"start":"08:00","end":"18:00"},
    {"day":4,"open":true,"start":"08:00","end":"18:00"},
    {"day":5,"open":false,"start":"09:00","end":"18:00"},
    {"day":6,"open":false,"start":"09:00","end":"18:00"}
  ]'::jsonb,
  true
),
value_schema = '{
  "type": "object",
  "required": ["chat", "line", "email", "phone"],
  "properties": {
    "chat": {
      "type": "object",
      "required": ["enabled", "hours"],
      "properties": {
        "enabled": { "type": "boolean" },
        "url": { "type": ["string", "null"], "maxLength": 500 },
        "hours": {
          "type": "array",
          "minItems": 7,
          "maxItems": 7,
          "items": {
            "type": "object",
            "required": ["day", "open", "start", "end"],
            "properties": {
              "day": { "type": "integer", "minimum": 0, "maximum": 6 },
              "open": { "type": "boolean" },
              "start": { "type": "string", "pattern": "^([01]\\d|2[0-3]):[0-5]\\d$" },
              "end": { "type": "string", "pattern": "^([01]\\d|2[0-3]):[0-5]\\d$" }
            }
          }
        }
      }
    },
    "line": {
      "type": "object",
      "required": ["enabled"],
      "properties": {
        "enabled": { "type": "boolean" },
        "id": { "type": ["string", "null"], "maxLength": 80 },
        "url": { "type": ["string", "null"], "maxLength": 500 }
      }
    },
    "email": {
      "type": "object",
      "required": ["enabled", "address"],
      "properties": {
        "enabled": { "type": "boolean" },
        "address": { "type": "string", "maxLength": 255 }
      }
    },
    "phone": {
      "type": "object",
      "required": ["enabled", "number", "hours"],
      "properties": {
        "enabled": { "type": "boolean" },
        "number": { "type": "string", "maxLength": 40 },
        "hours": {
          "type": "array",
          "minItems": 7,
          "maxItems": 7,
          "items": {
            "type": "object",
            "required": ["day", "open", "start", "end"],
            "properties": {
              "day": { "type": "integer", "minimum": 0, "maximum": 6 },
              "open": { "type": "boolean" },
              "start": { "type": "string", "pattern": "^([01]\\d|2[0-3]):[0-5]\\d$" },
              "end": { "type": "string", "pattern": "^([01]\\d|2[0-3]):[0-5]\\d$" }
            }
          }
        }
      }
    }
  }
}'::jsonb,
modified_date_time = now()
WHERE setting_key = 'support.contact'
  AND (
    jsonb_typeof(setting_value #> '{chat,hours}') IS DISTINCT FROM 'array'
    OR jsonb_array_length(COALESCE(setting_value #> '{chat,hours}', '[]'::jsonb)) <> 7
    OR jsonb_typeof(setting_value #> '{phone,hours}') IS DISTINCT FROM 'array'
    OR jsonb_array_length(COALESCE(setting_value #> '{phone,hours}', '[]'::jsonb)) <> 7
  );
