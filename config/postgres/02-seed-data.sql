-- =============================================================================
-- kdiab seed data -- realistic 30-day dataset for testing and development
--
-- Users (from Keycloak realm):
--   sarah  (11111111-...) PATIENT  glucose_unit=mg/dL  (stored in user_settings DB)
--   mike   (22222222-...) PATIENT  glucose_unit=mmol/L (stored in user_settings DB)
--
-- Each user gets:
--   - 30 days of CGM readings every 5 minutes (sarah: 70-200 mg/dL range,
--     mike: similar but stored in mg/dL, displayed in mmol/L via frontend)
--   - BGM checks (3-5 per day)
--   - Bolus + carbs treatment pairs (3 per day)
--   - One active pump basal profile
--   - DEVICE_STATUS treatments (pump snapshot every 5 min for 30 days)
--   - user_settings row (timezone, language, units, alarm thresholds)
--
-- Doctor-patient assignments (kdiab-users):
--   dr_house   (33333333-...) DOCTOR -> sarah
--   dr_cameron (44444444-...) DOCTOR -> mike
-- =============================================================================

\c "kdiab-measures"

-- -- Measures (CGM + BGM) ------------------------------------------------------
-- Generate ~8640 CGM readings per user (30 days × 288 per day, sampled here
-- as representative data using generate_series).

DO $$
DECLARE
  sarah_id UUID := '11111111-1111-1111-1111-111111111111';
  mike_id  UUID := '22222222-2222-2222-2222-222222222222';
  t        TIMESTAMPTZ;
  glucose  INTEGER;
  trend    TEXT;
  trends   TEXT[] := ARRAY['Flat','FortyFiveUp','FortyFiveDown','SingleUp','SingleDown'];
  base_ts  TIMESTAMPTZ := NOW() - INTERVAL '30 days';
BEGIN
  -- Skip if already seeded (idempotent re-run guard)
  IF EXISTS (SELECT 1 FROM measures LIMIT 1) THEN RETURN; END IF;

  -- Sarah -- CGM readings every 5 minutes for 30 days
  FOR i IN 0..8639 LOOP
    t       := base_ts + (i * INTERVAL '5 minutes');
    -- Simulate a realistic CGM curve: mostly in-range with some excursions
    glucose := 100 + ROUND(
                 70 * SIN(EXTRACT(EPOCH FROM t) / 7200.0)
               + 25 * SIN(EXTRACT(EPOCH FROM t) / 1800.0)
               + 10 * (RANDOM() - 0.5)
              )::INTEGER;
    glucose := GREATEST(55, LEAST(300, glucose));
    trend   := trends[1 + (i % 5)];
    INSERT INTO measures(id, user_id, measured_at, type, source, data, status)
    VALUES (
      gen_random_uuid(), sarah_id, t, 'CGM', 'MANUAL',
      jsonb_build_object('value', glucose, 'unit', 'mg/dL', 'trend', trend),
      'ACTIVE'
    );
  END LOOP;

  -- Sarah -- BGM spot checks (~4 per day, clustered around meals)
  FOR i IN 0..119 LOOP
    t       := base_ts + (i * INTERVAL '6 hours') + (INTERVAL '30 minutes' * (i % 3));
    glucose := 85 + (i * 7 % 100);
    INSERT INTO measures(id, user_id, measured_at, type, source, data, status)
    VALUES (
      gen_random_uuid(), sarah_id, t, 'BGM', 'MANUAL',
      jsonb_build_object('value', glucose, 'unit', 'mg/dL'),
      'ACTIVE'
    );
  END LOOP;

  -- Mike -- CGM readings every 5 minutes for 30 days (slightly different profile)
  FOR i IN 0..8639 LOOP
    t       := base_ts + (i * INTERVAL '5 minutes');
    glucose := 115 + ROUND(
                 60 * SIN(EXTRACT(EPOCH FROM t) / 7200.0 + 1.0)
               + 20 * SIN(EXTRACT(EPOCH FROM t) / 3600.0)
               + 8  * (RANDOM() - 0.5)
              )::INTEGER;
    glucose := GREATEST(55, LEAST(280, glucose));
    trend   := trends[1 + ((i + 2) % 5)];
    INSERT INTO measures(id, user_id, measured_at, type, source, data, status)
    VALUES (
      gen_random_uuid(), mike_id, t, 'CGM', 'MANUAL',
      jsonb_build_object('value', glucose, 'unit', 'mg/dL', 'trend', trend),
      'ACTIVE'
    );
  END LOOP;

  -- Mike -- BGM spot checks
  FOR i IN 0..119 LOOP
    t       := base_ts + (i * INTERVAL '6 hours') + (INTERVAL '15 minutes' * (i % 4));
    glucose := 90 + (i * 11 % 90);
    INSERT INTO measures(id, user_id, measured_at, type, source, data, status)
    VALUES (
      gen_random_uuid(), mike_id, t, 'BGM', 'MANUAL',
      jsonb_build_object('value', glucose, 'unit', 'mg/dL'),
      'ACTIVE'
    );
  END LOOP;

END $$;

-- -- Treatments ----------------------------------------------------------------

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
  -- Skip if already seeded (idempotent re-run guard)
  IF EXISTS (SELECT 1 FROM treatments LIMIT 1) THEN RETURN; END IF;

  -- 3 bolus+carbs pairs per day per user for 30 days (breakfast, lunch, dinner)
  FOR day IN 0..29 LOOP
    -- Breakfast ~08:00  (carbs 10-30 g, bolus 1.0-3.0 U, ICR ~10)
    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '8 hours';
    carbs := 10 + (day % 21);
    units := ROUND((carbs / 10.0)::NUMERIC, 1);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Frühstück');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Humalog'), 'Frühstücksbolus');

    -- Lunch ~13:00  (carbs 20-45 g, bolus 2.0-4.5 U)
    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '13 hours';
    carbs := 20 + (day % 26);
    units := ROUND((carbs / 10.0)::NUMERIC, 1);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Mittagessen');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Humalog'), 'Mittagsbolus');

    -- Dinner ~19:00  (carbs 5-35 g, bolus 0.5-3.5 U)
    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '19 hours';
    carbs := 5 + (day % 31);
    units := GREATEST(ROUND((carbs / 10.0)::NUMERIC, 1), 0.5);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Abendessen');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Humalog'), 'Abendbolus');

    -- Mike -- same meal structure
    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '7 hours' + INTERVAL '30 minutes';
    carbs := 15 + (day % 21);
    units := ROUND((carbs / 10.0)::NUMERIC, 1);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Frühstück');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Novolog'), 'Frühstücksbolus');

    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '12 hours' + INTERVAL '30 minutes';
    carbs := 20 + (day % 26);
    units := ROUND((carbs / 10.0)::NUMERIC, 1);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Mittagessen');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Novolog'), 'Mittagsbolus');

    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '18 hours' + INTERVAL '30 minutes';
    carbs := 5 + (day % 26);
    units := GREATEST(ROUND((carbs / 10.0)::NUMERIC, 1), 0.5);
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t, 'CARBS',
            jsonb_build_object('carbs', carbs), 'Abendessen');
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), mike_id, t + INTERVAL '5 minutes', 'BOLUS',
            jsonb_build_object('insulin', units, 'insulinType', 'Novolog'), 'Abendbolus');

    -- Occasional correction bolus for sarah (every 3 days)
    IF day % 3 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '15 hours';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), sarah_id, t, 'CORRECTION_BOLUS',
              jsonb_build_object('insulin', 1.5, 'insulinType', 'Humalog'), 'High correction');
    END IF;

    -- Exercise entries for sarah every day (alternating morning/evening)
    IF day % 2 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '7 hours';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), sarah_id, t, 'EXERCISE',
              jsonb_build_object('duration', 30, 'intensity', 'moderate'), 'Morning walk');
    ELSE
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '17 hours' + INTERVAL '30 minutes';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), sarah_id, t, 'EXERCISE',
              jsonb_build_object('duration', 45, 'intensity', 'low'), 'Evening yoga');
    END IF;

    -- Exercise entries for mike every day
    IF day % 2 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '6 hours' + INTERVAL '30 minutes';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), mike_id, t, 'EXERCISE',
              jsonb_build_object('duration', 40, 'intensity', 'moderate'), 'Morning run');
    ELSE
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '18 hours';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), mike_id, t, 'EXERCISE',
              jsonb_build_object('duration', 60, 'intensity', 'high'), 'Cycling');
    END IF;

    -- Temp basal for sarah: daily entries covering BELOW / ABOVE / SUSPENDED states
    -- BELOW (every day during exercise window)
    t := base_ts + (day * INTERVAL '1 day') + INTERVAL '6 hours' + INTERVAL '45 minutes';
    INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
    VALUES (gen_random_uuid(), sarah_id, t, 'TEMP_BASAL',
            jsonb_build_object('rate', 0.45, 'duration', 60, 'percent', 50), 'Reduced for exercise');
    -- ABOVE (every 3 days: post-meal high correction via increased basal)
    IF day % 3 = 1 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '14 hours' + INTERVAL '30 minutes';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), sarah_id, t, 'TEMP_BASAL',
              jsonb_build_object('rate', 2.0, 'duration', 30, 'percent', 200), 'High correction temp');
    END IF;
    -- SUSPENDED (every 7 days: pump suspend for site change)
    IF day % 7 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '9 hours' + INTERVAL '5 minutes';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), sarah_id, t, 'TEMP_BASAL',
              jsonb_build_object('rate', 0.0, 'duration', 5, 'percent', 0), 'Suspended for site change');
    END IF;

    -- Hypo treatment for sarah (every 4 days)
    IF day % 4 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '3 hours' + INTERVAL '30 minutes';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), sarah_id, t, 'HYPO_TREATMENT',
              jsonb_build_object('carbs', 15, 'reason', 'night hypo'), 'Treated low');
    END IF;

    -- Hypo treatment for mike (every 6 days)
    IF day % 6 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '14 hours';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), mike_id, t, 'HYPO_TREATMENT',
              jsonb_build_object('carbs', 12, 'reason', 'post-exercise low'), NULL);
    END IF;

    -- Notes for sarah (every 5 days)
    IF day % 5 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '20 hours';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), sarah_id, t, 'NOTE',
              jsonb_build_object('text', CASE day % 10
                WHEN 0 THEN 'Felt tired today, skipped afternoon walk'
                ELSE 'Good control, stress at work'
              END), NULL);
    END IF;

    -- Notes for mike (every 7 days)
    IF day % 7 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '21 hours';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), mike_id, t, 'NOTE',
              jsonb_build_object('text', 'Travel day -- meals irregular'), NULL);
    END IF;

    -- Site change every 3 days for sarah
    IF day % 3 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '9 hours';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), sarah_id, t, 'SITE_CHANGE',
              jsonb_build_object('location', CASE (day / 3) % 4
                WHEN 0 THEN 'abdomen left'
                WHEN 1 THEN 'abdomen right'
                WHEN 2 THEN 'upper arm left'
                ELSE 'upper arm right'
              END), 'Infusion set change');
    END IF;

    -- Site change every 3 days for mike
    IF day % 3 = 1 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '8 hours' + INTERVAL '30 minutes';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), mike_id, t, 'SITE_CHANGE',
              jsonb_build_object('location', CASE (day / 3) % 2
                WHEN 0 THEN 'abdomen'
                ELSE 'upper arm'
              END), NULL);
    END IF;

    -- Sensor insert every 7 days for sarah
    IF day % 7 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '9 hours' + INTERVAL '15 minutes';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), sarah_id, t, 'SENSOR_INSERT',
              jsonb_build_object('sensor', 'Dexcom G7'), 'New sensor');
    END IF;

    -- Sensor insert every 10 days for mike
    IF day % 10 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '8 hours';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), mike_id, t, 'SENSOR_INSERT',
              jsonb_build_object('sensor', 'Libre 3'), NULL);
    END IF;

    -- Insulin change every 14 days for sarah
    IF day % 14 = 0 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '9 hours' + INTERVAL '30 minutes';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), sarah_id, t, 'INSULIN_CHANGE',
              jsonb_build_object('insulinType', 'Humalog'), 'New cartridge');
    END IF;

    -- Insulin change every 14 days for mike
    IF day % 14 = 1 THEN
      t := base_ts + (day * INTERVAL '1 day') + INTERVAL '8 hours' + INTERVAL '45 minutes';
      INSERT INTO treatments(id, user_id, treated_at, type, data, notes)
      VALUES (gen_random_uuid(), mike_id, t, 'INSULIN_CHANGE',
              jsonb_build_object('insulinType', 'Novolog'), NULL);
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

-- -- Device Status (pump snapshots every 5 minutes for 30 days) ---------------
-- Reservoir drains from ~300 U to ~0 U over 3 days, then refills (site change).
-- Battery drains from 100% to ~20% over 7 days, then recharges.

-- Sarah: AAPS / Dana RS
INSERT INTO device_status(id, user_id, recorded_at, created_at, device, pump_name, reservoir_units, battery_level, pump_connected)
SELECT
  gen_random_uuid(),
  '11111111-1111-1111-1111-111111111111',
  NOW() - (8639 - s) * INTERVAL '5 minutes',
  NOW() - (8639 - s) * INTERVAL '5 minutes',
  'AAPS 3.2.0',
  'Dana RS',
  ROUND(GREATEST(0::numeric, 300 - ((s % 864) * 300.0 / 864)), 1),
  100 - ((s % 2016) * 80 / 2016),
  true
FROM generate_series(0, 8639) AS s;

-- Mike: xDrip+ / Omnipod 5
INSERT INTO device_status(id, user_id, recorded_at, created_at, device, pump_name, reservoir_units, battery_level, pump_connected)
SELECT
  gen_random_uuid(),
  '22222222-2222-2222-2222-222222222222',
  NOW() - (8639 - s) * INTERVAL '5 minutes',
  NOW() - (8639 - s) * INTERVAL '5 minutes',
  'xDrip+ 2024.01.15',
  'Omnipod 5',
  ROUND(GREATEST(0::numeric, 280 - ((s % 864) * 280.0 / 864)), 1),
  100 - (((s + 500) % 2016) * 80 / 2016),
  true
FROM generate_series(0, 8639) AS s;

-- -- Profiles ------------------------------------------------------------------

\c "kdiab-profiles"

-- Insulin reference data
INSERT INTO insulins(id, name) VALUES
  ('0195a850-2527-7cdb-8fde-6cd2e9122fb1', 'Humalog'),
  ('0195a850-2527-7cdb-8fde-6cd2e9122fb2', 'Novolog'),
  ('0195a850-2527-7cdb-8fde-6cd2e9122fb3', 'Fiasp'),
  ('0195a850-2527-7cdb-8fde-6cd2e9122fb4', 'Lyumjev'),
  ('0195a850-2527-7cdb-8fde-6cd2e9122fb5', 'Apidra')
ON CONFLICT DO NOTHING;

-- Sarah -- archived profile from 60 days ago, active profile from 30 days ago
INSERT INTO profiles(id, user_id, name, insulin_type, duration_of_action, time_zone, created_at, segments, carb_absorption_rate_g_per_hour)
VALUES (
  'aaaa0001-0000-0000-0000-000000000001',
  '11111111-1111-1111-1111-111111111111',
  'Sarah Profile v1',
  'Humalog',
  240,
  'Europe/Berlin',
  NOW() - INTERVAL '65 days',
  '{"basal":[{"startTime":"00:00","value":0.85},{"startTime":"06:00","value":1.10},{"startTime":"12:00","value":0.90},{"startTime":"18:00","value":1.00}],"icr":[{"startTime":"00:00","value":10.0},{"startTime":"12:00","value":12.0}],"isf":[{"startTime":"00:00","value":50.0},{"startTime":"14:00","value":45.0}],"targets":[{"startTime":"00:00","low":80.0,"high":120.0}]}',
  20.0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO profile_statuses(profile_id, user_id, status, valid_from)
VALUES (
  'aaaa0001-0000-0000-0000-000000000001',
  '11111111-1111-1111-1111-111111111111',
  'ARCHIVED',
  NOW() - INTERVAL '65 days'
)
ON CONFLICT (profile_id) DO NOTHING;

INSERT INTO profiles(id, user_id, previous_profile_id, name, insulin_type, duration_of_action, time_zone, created_at, segments, carb_absorption_rate_g_per_hour)
VALUES (
  'aaaa0002-0000-0000-0000-000000000002',
  '11111111-1111-1111-1111-111111111111',
  'aaaa0001-0000-0000-0000-000000000001',
  'Sarah Profile v2',
  'Humalog',
  240,
  'Europe/Berlin',
  NOW() - INTERVAL '31 days',
  '{"basal":[{"startTime":"00:00","value":0.90},{"startTime":"06:00","value":1.20},{"startTime":"10:00","value":1.00},{"startTime":"18:00","value":1.05},{"startTime":"22:00","value":0.80}],"icr":[{"startTime":"00:00","value":10.0},{"startTime":"12:00","value":12.0}],"isf":[{"startTime":"00:00","value":50.0},{"startTime":"14:00","value":45.0}],"targets":[{"startTime":"00:00","low":80.0,"high":120.0}],"insulinToMealInterval":[{"startTime":"00:00","minutes":15},{"startTime":"06:00","minutes":20},{"startTime":"10:00","minutes":10}]}',
  20.0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO profile_statuses(profile_id, user_id, status, valid_from)
VALUES (
  'aaaa0002-0000-0000-0000-000000000002',
  '11111111-1111-1111-1111-111111111111',
  'ACTIVE',
  NOW() - INTERVAL '30 days'
)
ON CONFLICT (profile_id) DO NOTHING;

-- Mike -- archived profile v1 (45 days ago) + active profile v2 (20 days ago)
INSERT INTO profiles(id, user_id, name, insulin_type, duration_of_action, time_zone, created_at, segments, carb_absorption_rate_g_per_hour)
VALUES (
  'bbbb0001-0000-0000-0000-000000000001',
  '22222222-2222-2222-2222-222222222222',
  'Mike Profile v1',
  'Novolog',
  210,
  'America/New_York',
  NOW() - INTERVAL '45 days',
  '{"basal":[{"startTime":"00:00","value":0.70},{"startTime":"06:00","value":0.95},{"startTime":"12:00","value":0.75},{"startTime":"20:00","value":0.65}],"icr":[{"startTime":"00:00","value":12.0},{"startTime":"12:00","value":15.0}],"isf":[{"startTime":"00:00","value":45.0},{"startTime":"14:00","value":40.0}],"targets":[{"startTime":"00:00","low":80.0,"high":120.0}]}',
  25.0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO profile_statuses(profile_id, user_id, status, valid_from)
VALUES (
  'bbbb0001-0000-0000-0000-000000000001',
  '22222222-2222-2222-2222-222222222222',
  'ARCHIVED',
  NOW() - INTERVAL '45 days'
)
ON CONFLICT (profile_id) DO UPDATE SET status = 'ARCHIVED';

-- Mike v2: slightly increased overnight basal after doctor review at day -20
INSERT INTO profiles(id, user_id, previous_profile_id, name, insulin_type, duration_of_action, time_zone, created_at, segments, carb_absorption_rate_g_per_hour)
VALUES (
  'bbbb0002-0000-0000-0000-000000000002',
  '22222222-2222-2222-2222-222222222222',
  'bbbb0001-0000-0000-0000-000000000001',
  'Mike Profile v2',
  'Novolog',
  210,
  'America/New_York',
  NOW() - INTERVAL '20 days',
  '{"basal":[{"startTime":"00:00","value":0.80},{"startTime":"06:00","value":1.00},{"startTime":"12:00","value":0.80},{"startTime":"20:00","value":0.70}],"icr":[{"startTime":"00:00","value":12.0},{"startTime":"12:00","value":15.0}],"isf":[{"startTime":"00:00","value":45.0},{"startTime":"14:00","value":40.0}],"targets":[{"startTime":"00:00","low":80.0,"high":120.0}],"insulinToMealInterval":[{"startTime":"00:00","minutes":10},{"startTime":"06:00","minutes":15}]}',
  25.0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO profile_statuses(profile_id, user_id, status, valid_from)
VALUES (
  'bbbb0002-0000-0000-0000-000000000002',
  '22222222-2222-2222-2222-222222222222',
  'ACTIVE',
  NOW() - INTERVAL '20 days'
)
ON CONFLICT (profile_id) DO NOTHING;

-- =============================================================================
-- kdiab-users seed data
--
-- user_settings: sarah (mg/dL), dr_house, dr_cameron, admin -- mike intentionally omitted
--   so that mike's first login exercises the "new user" path (no settings row exists yet).
-- doctor_patient: dr_house->sarah, dr_cameron->mike (mirrors Keycloak assignments)
-- =============================================================================

\c "kdiab-users"

-- -- User Settings -------------------------------------------------------------
-- ISO 8601 helper: produces '2025-11-19T12:34:56.789012Z' -- required because
-- the created_at/updated_at columns are varchar(50) and Instant.parse() expects T not space.
INSERT INTO user_settings(user_id, timezone, language, time_format, glucose_unit, weight_unit,
                          alarm_urgent_high, alarm_high, alarm_low, alarm_urgent_low,
                          created_at, updated_at)
VALUES
  -- sarah: mg/dL, European defaults, German locale
  ('11111111-1111-1111-1111-111111111111', 'Europe/Berlin', 'de', 24, 'mg/dL', 'kg',
   250, 180, 70, 54,
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  -- mike: mmol/L, US defaults, English locale
  ('22222222-2222-2222-2222-222222222222', 'America/New_York', 'en', 12, 'mmol/L', 'lb',
   13, 10, 3.9, 3,
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  -- dr_house
  ('33333333-3333-3333-3333-333333333333', 'America/New_York', 'en', 12, 'mg/dL', 'lb',
   NULL, NULL, NULL, NULL,
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  -- dr_cameron
  ('44444444-4444-4444-4444-444444444444', 'America/New_York', 'en', 12, 'mg/dL', 'lb',
   NULL, NULL, NULL, NULL,
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  -- admin
  ('55555555-5555-5555-5555-555555555555', 'UTC', 'en', 24, 'mg/dL', 'kg',
   NULL, NULL, NULL, NULL,
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))
ON CONFLICT (user_id) DO NOTHING;

-- -- Doctor-Patient Assignments ------------------------------------------------
INSERT INTO doctor_patient(doctor_id, patient_id, created_at)
VALUES
  -- dr_house is assigned to sarah
  ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111',
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  -- dr_cameron is assigned to mike
  ('44444444-4444-4444-4444-444444444444', '22222222-2222-2222-2222-222222222222',
   TO_CHAR((NOW() AT TIME ZONE 'UTC') - INTERVAL '90 days', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))
ON CONFLICT (doctor_id, patient_id) DO NOTHING;

-- User Profile (birthday) -----------------------------------------------
INSERT INTO user_profile(user_id, birthday)
VALUES
  ('11111111-1111-1111-1111-111111111111', '1990-05-15'),
  ('22222222-2222-2222-2222-222222222222', '1985-03-22')
ON CONFLICT (user_id) DO NOTHING;
