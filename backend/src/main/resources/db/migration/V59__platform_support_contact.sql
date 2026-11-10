-- Platform support contact shown on /help "ติดต่อเรา".
-- Uses existing platform_settings (V1). Idempotent seed only — no schema change.

INSERT INTO platform_settings (setting_key, setting_value, is_admin_editable, value_schema, description)
VALUES (
  'support.contact',
  '{
    "chat": {
      "enabled": true,
      "url": null,
      "hours": {
        "th": "จ.–อา. 08:00 - 22:00",
        "en": "Mon–Sun 08:00 - 22:00",
        "zh": "周一至周日 08:00 - 22:00"
      }
    },
    "line": {
      "enabled": true,
      "id": "@thaishopfun",
      "url": null
    },
    "email": {
      "enabled": true,
      "address": "support@thaishopfun.com"
    },
    "phone": {
      "enabled": true,
      "number": "02-123-4567",
      "hours": {
        "th": "จ.–ศ. 08:00 - 18:00",
        "en": "Mon–Fri 08:00 - 18:00",
        "zh": "周一至周五 08:00 - 18:00"
      }
    }
  }'::jsonb,
  true,
  '{
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
            "type": "object",
            "properties": {
              "th": { "type": "string", "maxLength": 100 },
              "en": { "type": "string", "maxLength": 100 },
              "zh": { "type": "string", "maxLength": 100 }
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
            "type": "object",
            "properties": {
              "th": { "type": "string", "maxLength": 100 },
              "en": { "type": "string", "maxLength": 100 },
              "zh": { "type": "string", "maxLength": 100 }
            }
          }
        }
      }
    }
  }'::jsonb,
  'ช่องทางติดต่อแพลตฟอร์มที่แสดงในหน้า /help (แชท, LINE, อีเมล, โทร) — ห้ามเก็บ secret'
)
ON CONFLICT (setting_key) DO NOTHING;
