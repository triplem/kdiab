-- =============================================================================
-- kdiab seed data — realistic 30-day dataset for testing and development
--
-- Users (from Keycloak realm):
--   sarah  (11111111-...) PATIENT  glucose_unit=mg/dL
--   mike   (22222222-...) PATIENT  glucose_unit=mmol/L
--
-- Each user gets:
--   - 30 days of CGM readings every 5 minutes (sarah: 70–200 mg/dL range,
--     mike: similar but stored in mg/dL, displayed in mmol/L via frontend)
--   - BGM checks (3-5 per day)
--   - Bolus + carbs treatment pairs (3 per day)
--   - One active pump basal profile
-- =============================================================================

\c "kdiab-measures"

-- ── Measures (CGM + BGM) ──────────────────────────────────────────────────────
-- Generate ~8640 CGM readings per user (30 days × 288 per day, sampled here
-- as representative data using generate_series).

DO $$
DECLARE
  sarah_id UUID := '11111111-1111-1111-1111-111111111111';
  mike_id  UUID := '22222222-2222-2222-2222-222222222222';
  t        TIMESTAMPTZ;
  sgv      INTEGER;
  trend    TEXT;
  trends   TEXT[] := ARRAY['Flat','FortyFiveUp','FortyFiveDown','SingleUp','SingleDown'];
  base_ts  TIMESTAMPTZ := NOW() - INTERVAL '30 days';
BEGIN
  -- Sarah — CGM readings every 5 minutes for 30 days
  FOR i IN 0..8639 LOOP
    t   := base_ts + (i * INTERVAL '5 minutes');
    -- Simulate a realistic CGM curve: mostly in-range with some excursions
    sgv := 100 + ROUND(
              70 * SIN(EXTRACT(EPOCH FROM t) / 7200.0)
            + 25 * SIN(EXTRACT(EPOCH FROM t) / 1800.0)
            + 10 * (RANDOM() - 0.5)
           )::INTEGER;
    sgv := GREATEST(55, LEAST(300, sgv));
    trend := trends[1 + (i % 5)];
    INSERT INTO measures(id, user_id, measured_at, type, source, data, status)
    VALUES (
      gen_random_uuid(), sarah_id, t, 'CGM', 'NIGHTSCOUT',
      jsonb_build_object('sgv', sgv, 'trend', trend),
      'ACTIVE'
    );
  END LOOP;

  -- Sarah — BGM spot checks (~4 per day, clustered around meals)
  FOR i IN 0..119 LOOP
    t   := base_ts + (i * INTERVAL '6 hours') + (INTERVAL '30 minutes' * (i % 3));
    sgv := 85 + (i * 7 % 100);
    INSERT INTO measures(id, user_id, measured_at, type, source, data, status)
    VALUES (
      gen_random_uuid(), sarah_id, t, 'BGM', 'MANUAL',
      jsonb_build_object('mbg', sgv),
      'ACTIVE'
    );
  END LOOP;

  -- Mike — CGM readings every 5 minutes for 30 days (slightly different profile)
  FOR i IN 0..8639 LOOP
    t   := base_ts + (i * INTERVAL '5 minutes');
    sgv := 115 + ROUND(
              60 * SIN(EXTRACT(EPOCH FROM t) / 7200.0 + 1.0)
            + 20 * SIN(EXTRACT(EPOCH FROM t) / 3600.0)
            + 8  * (RANDOM() - 0.5)
           )::INTEGER;
    sgv := GREATEST(55, LEAST(280, sgv));
    trend := trends[1 + ((i + 2) % 5)];
    INSERT INTO measures(id, user_id, measured_at, type, source, data, status)
    VALUES (
      gen_random_uuid(), mike_id, t, 'CGM', 'NIGHTSCOUT',
      jsonb_build_object('sgv', sgv, 'trend', trend),
      'ACTIVE'
    );
  END LOOP;

  -- Mike — BGM spot checks
  FOR i IN 0..119 LOOP
    t   := base_ts + (i * INTERVAL '6 hours') + (INTERVAL '15 minutes' * (i % 4));
    sgv := 90 + (i * 11 % 90);
    INSERT INTO measures(id, user_id, measured_at, type, source, data, status)
    VALUES (
      gen_random_uuid(), mike_id, t, 'BGM', 'MANUAL',
      jsonb_build_object('mbg', sgv),
      'ACTIVE'
    );
  END LOOP;

END $$;

-- ── Treatments ────────────────────────────────────────────────────────────────

\c "kdiab-treatments"

DO $$
DECLARE
  sarah_id UUID := '11111111-1111-1111-1111-111111111111';
  mike_id  UUID := '22222222-2222-2222-2222-222222222222';
  base_ts  TIMESTAMPTZ := NOW() - INTERVAL '30 days';
  t        TIMESTAMPTZ;
  carbs    INTEGER;
  units    NUMERIC;
BEGIN
  -- 3 bolus+carbs pairs per day per user for 30 days (breakfast, lunch, dinner)
  FOR day IN 0..29 LOOP
    -- Breakfast ~08:00
    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '8 hours';
    carbs := 45 + (day % 20);
    units := ROUND((carbs / 10.0 + 0.5)::NUMERIC, 1);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Frühstück');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Humalog'), 'Breakfast bolus');

    -- Lunch ~13:00
    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '13 hours';
    carbs := 55 + (day % 30);
    units := ROUND((carbs / 10.0 + 0.3)::NUMERIC, 1);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Mittagessen');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Humalog'), 'Lunch bolus');

    -- Dinner ~19:00
    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '19 hours';
    carbs := 65 + (day % 25);
    units := ROUND((carbs / 10.0 + 0.8)::NUMERIC, 1);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Abendessen');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Humalog'), 'Dinner bolus');

    -- Mike — same meal structure
    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '7 hours' + INTERVAL '30 minutes';
    carbs := 40 + (day % 25);
    units := ROUND((carbs / 10.0 + 0.4)::NUMERIC, 1);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Frühstück');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Novolog'), 'Breakfast bolus');

    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '12 hours' + INTERVAL '30 minutes';
    carbs := 50 + (day % 20);
    units := ROUND((carbs / 10.0 + 0.2)::NUMERIC, 1);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Mittagessen');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Novolog'), 'Lunch bolus');

    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '18 hours' + INTERVAL '30 minutes';
    carbs := 60 + (day % 30);
    units := ROUND((carbs / 10.0 + 0.6)::NUMERIC, 1);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Abendessen');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Novolog'), 'Dinner bolus');

    -- Occasional correction bolus for sarah (every 3 days)
    IF day % 3 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '15 hours';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), sarah_id, t, 'CORRECTION_BOLUS',
              jsonb_build_object('insulin', 1.5, 'insulinType', 'Humalog'), 'High correction');
    END IF;
  END LOOP;

  -- Weekly activity entries for sarah (running, moderate)
  INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
  VALUES (gen_random_uuid(), sarah_id, NOW() - INTERVAL '7 days', 'ACTIVITY',
          jsonb_build_object('name', 'Laufen', 'duration', 45, 'intensity', 'moderate'), 'Morgenrunde');
  INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
  VALUES (gen_random_uuid(), sarah_id, NOW() - INTERVAL '14 days', 'ACTIVITY',
          jsonb_build_object('name', 'Radfahren', 'duration', 60, 'intensity', 'low'), 'Abendtour');

  -- Weekly activity entries for mike (cycling, jogging)
  INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
  VALUES (gen_random_uuid(), mike_id, NOW() - INTERVAL '5 days', 'ACTIVITY',
          jsonb_build_object('name', 'Joggen', 'duration', 30, 'intensity', 'moderate'), 'Mittagspause');
  INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
  VALUES (gen_random_uuid(), mike_id, NOW() - INTERVAL '10 days', 'ACTIVITY',
          jsonb_build_object('name', 'Schwimmen', 'duration', 45, 'intensity', 'high'), 'Abendtraining');
END $$;

-- ── Profiles ──────────────────────────────────────────────────────────────────

\c "kdiab-profiles"

-- Insulin reference data
INSERT INTO insulins(id, name) VALUES
  ('0195a850-2527-7cdb-8fde-6cd2e9122fb1', 'Humalog'),
  ('0195a850-2527-7cdb-8fde-6cd2e9122fb2', 'Novolog'),
  ('0195a850-2527-7cdb-8fde-6cd2e9122fb3', 'Fiasp'),
  ('0195a850-2527-7cdb-8fde-6cd2e9122fb4', 'Lyumjev'),
  ('0195a850-2527-7cdb-8fde-6cd2e9122fb5', 'Apidra');

-- Sarah — archived profile from 60 days ago, active profile from 30 days ago
INSERT INTO profiles(id, user_id, name, insulin_type, units, duration_of_action, time_zone, created_at, segments)
VALUES (
  'aaaa0001-0000-0000-0000-000000000001',
  '11111111-1111-1111-1111-111111111111',
  'Sarah Profile v1',
  'Humalog',
  'mg/dl',
  240,
  'Europe/Berlin',
  NOW() - INTERVAL '65 days',
  '[{"start":"00:00","basal":0.85},{"start":"06:00","basal":1.10},{"start":"12:00","basal":0.90},{"start":"18:00","basal":1.00}]'
);

INSERT INTO profile_statuses(profile_id, user_id, status, valid_from)
VALUES (
  'aaaa0001-0000-0000-0000-000000000001',
  '11111111-1111-1111-1111-111111111111',
  'ARCHIVED',
  NOW() - INTERVAL '65 days'
);

INSERT INTO profiles(id, user_id, previous_profile_id, name, insulin_type, units, duration_of_action, time_zone, created_at, segments)
VALUES (
  'aaaa0002-0000-0000-0000-000000000002',
  '11111111-1111-1111-1111-111111111111',
  'aaaa0001-0000-0000-0000-000000000001',
  'Sarah Profile v2',
  'Humalog',
  'mg/dl',
  240,
  'Europe/Berlin',
  NOW() - INTERVAL '31 days',
  '[{"start":"00:00","basal":0.90},{"start":"06:00","basal":1.20},{"start":"10:00","basal":1.00},{"start":"18:00","basal":1.05},{"start":"22:00","basal":0.80}]'
);

INSERT INTO profile_statuses(profile_id, user_id, status, valid_from)
VALUES (
  'aaaa0002-0000-0000-0000-000000000002',
  '11111111-1111-1111-1111-111111111111',
  'ACTIVE',
  NOW() - INTERVAL '30 days'
);

-- Mike — one active profile
INSERT INTO profiles(id, user_id, name, insulin_type, units, duration_of_action, time_zone, created_at, segments)
VALUES (
  'bbbb0001-0000-0000-0000-000000000001',
  '22222222-2222-2222-2222-222222222222',
  'Mike Profile v1',
  'Novolog',
  'mg/dl',
  210,
  'America/New_York',
  NOW() - INTERVAL '45 days',
  '[{"start":"00:00","basal":0.70},{"start":"06:00","basal":0.95},{"start":"12:00","basal":0.75},{"start":"20:00","basal":0.65}]'
);

INSERT INTO profile_statuses(profile_id, user_id, status, valid_from)
VALUES (
  'bbbb0001-0000-0000-0000-000000000001',
  '22222222-2222-2222-2222-222222222222',
  'ACTIVE',
  NOW() - INTERVAL '45 days'
);
