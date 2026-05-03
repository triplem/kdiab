import React, { useState } from 'react';
import { useTimeFormat } from '../../context/TimeFormatContext';
import { useTranslation } from 'react-i18next';

type PatientTreatmentType =
  | 'BOLUS' | 'CARBS' | 'CORRECTION_BOLUS' | 'BASAL'
  | 'COMBO_BOLUS' | 'TEMP_BASAL' | 'PUMP_SUSPEND'
  | 'EXERCISE' | 'NOTE' | 'SITE_CHANGE' | 'SENSOR_INSERT' | 'INSULIN_CHANGE' | 'ACTIVITY';

export interface TreatmentInput {
  type: string;
  treatedAt: string;
  data: Record<string, unknown>;
  notes?: string;
}

interface AddTreatmentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (treatment: TreatmentInput) => void;
  isSaving?: boolean;
  error?: string | null;
}

const TREATMENT_TYPES: PatientTreatmentType[] = [
  'BOLUS', 'CORRECTION_BOLUS', 'COMBO_BOLUS', 'CARBS', 'BASAL',
  'TEMP_BASAL', 'PUMP_SUSPEND', 'EXERCISE', 'NOTE',
  'SITE_CHANGE', 'SENSOR_INSERT', 'INSULIN_CHANGE', 'ACTIVITY',
];

const inputStyle: React.CSSProperties = {
  padding: '8px',
  border: '1px solid var(--border-color)',
  borderRadius: '4px',
  fontSize: '1rem',
  width: '100%',
  boxSizing: 'border-box',
};

const labelStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '4px',
};

function nowRounded(): string {
  const d = new Date();
  d.setSeconds(0, 0);
  return d.toISOString().slice(0, 16);
}

export const AddTreatmentModal: React.FC<AddTreatmentModalProps> = ({ isOpen, onClose, onSave, isSaving = false, error }) => {
  const { locale } = useTimeFormat();
  const { t } = useTranslation();
  const [type, setType] = useState<PatientTreatmentType>('BOLUS');
  const [treatedAt, setTreatedAt] = useState(nowRounded);
  const [notes, setNotes] = useState('');

  const [insulinUnits, setInsulinUnits] = useState('');
  const [insulinType, setInsulinType] = useState('');

  const [basalInsulin, setBasalInsulin] = useState('');
  const [basalInsulinType, setBasalInsulinType] = useState('');
  const [basalDurationHours, setBasalDurationHours] = useState('');

  const [comboInsulin, setComboInsulin] = useState('');
  const [comboSplitNow, setComboSplitNow] = useState('50');
  const [comboDurationMin, setComboDurationMin] = useState('');

  const [tempBasalRate, setTempBasalRate] = useState('');
  const [tempBasalDurationMin, setTempBasalDurationMin] = useState('');
  const [tempBasalAbsolute, setTempBasalAbsolute] = useState(true);

  const [pumpSuspendDurationMin, setPumpSuspendDurationMin] = useState('');
  const [pumpSuspendReason, setPumpSuspendReason] = useState('');

  const [carbs, setCarbs] = useState('');
  const [absorptionTime, setAbsorptionTime] = useState('');

  const [exerciseDuration, setExerciseDuration] = useState('');
  const [exerciseIntensity, setExerciseIntensity] = useState<'light' | 'moderate' | 'intense'>('moderate');

  const [noteText, setNoteText] = useState('');
  const [siteLocation, setSiteLocation] = useState('');
  const [sensorModel, setSensorModel] = useState('');
  const [newInsulinType, setNewInsulinType] = useState('');
  const [activityName, setActivityName] = useState('');
  const [activityDuration, setActivityDuration] = useState('');
  const [activityIntensity, setActivityIntensity] = useState<'low' | 'moderate' | 'high'>('moderate');

  if (!isOpen) return null;

  const buildData = (): Record<string, unknown> | null => {
    switch (type) {
      case 'BOLUS':
      case 'CORRECTION_BOLUS': {
        const v = parseFloat(insulinUnits);
        if (isNaN(v) || v <= 0) return null;
        return insulinType ? { insulin: v, insulinType } : { insulin: v };
      }
      case 'COMBO_BOLUS': {
        const v = parseFloat(comboInsulin);
        if (isNaN(v) || v <= 0) return null;
        const splitNow = parseFloat(comboSplitNow);
        const dur = parseInt(comboDurationMin, 10);
        return {
          insulin: v,
          splitNow: isNaN(splitNow) ? 50 : splitNow,
          splitExt: isNaN(splitNow) ? 50 : 100 - splitNow,
          ...(comboDurationMin && !isNaN(dur) && { duration: dur }),
        };
      }
      case 'BASAL': {
        const v = parseFloat(basalInsulin);
        if (isNaN(v) || v <= 0) return null;
        const durHours = parseFloat(basalDurationHours);
        return {
          insulin: v,
          ...(basalInsulinType && { insulinType: basalInsulinType }),
          // All durations are in minutes per API spec
          ...(basalDurationHours && !isNaN(durHours) && { duration: Math.round(durHours * 60) }),
        };
      }
      case 'TEMP_BASAL': {
        const rate = parseFloat(tempBasalRate);
        if (isNaN(rate) || rate < 0) return null;
        const dur = parseInt(tempBasalDurationMin, 10);
        if (isNaN(dur) || dur <= 0) return null;
        return { rate, duration: dur, absolute: tempBasalAbsolute };
      }
      case 'PUMP_SUSPEND': {
        const dur = parseInt(pumpSuspendDurationMin, 10);
        if (isNaN(dur) || dur <= 0) return null;
        return {
          duration: dur,
          ...(pumpSuspendReason.trim() && { reason: pumpSuspendReason.trim() }),
        };
      }
      case 'CARBS': {
        const v = parseFloat(carbs);
        if (isNaN(v) || v <= 0) return null;
        const abs = parseFloat(absorptionTime);
        return absorptionTime && !isNaN(abs)
          ? { carbs: v, absorptionTime: abs }
          : { carbs: v };
      }
      case 'EXERCISE': {
        const dur = parseInt(exerciseDuration, 10);
        if (isNaN(dur) || dur <= 0) return null;
        return { duration: dur, intensity: exerciseIntensity };
      }
      case 'NOTE': {
        if (!noteText.trim()) return null;
        return { text: noteText.trim() };
      }
      case 'SITE_CHANGE':
        return siteLocation ? { location: siteLocation } : {};
      case 'SENSOR_INSERT':
        return sensorModel ? { sensor: sensorModel } : {};
      case 'INSULIN_CHANGE':
        return newInsulinType ? { insulinType: newInsulinType } : {};
      case 'ACTIVITY': {
        const dur = parseInt(activityDuration, 10);
        if (!activityName.trim() || isNaN(dur) || dur <= 0) return null;
        return { name: activityName.trim(), duration: dur, intensity: activityIntensity };
      }
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const data = buildData();
    if (data === null) {
      alert(t('modal.validationError'));
      return;
    }
    onSave({
      type,
      treatedAt: new Date(treatedAt).toISOString(),
      data,
      ...(notes.trim() && { notes: notes.trim() }),
    });
  };

  const renderTypeFields = () => {
    switch (type) {
      case 'BOLUS':
      case 'CORRECTION_BOLUS':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('modal.insulinUnits')}</span>
              <input type="number" min="0.1" max="100" step="0.1" placeholder="e.g. 2.5"
                value={insulinUnits} onChange={e => setInsulinUnits(e.target.value)}
                style={inputStyle} required autoFocus />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.insulinType')}</span>
              <input type="text" placeholder="e.g. NovoRapid"
                value={insulinType} onChange={e => setInsulinType(e.target.value)}
                style={inputStyle} />
            </label>
          </>
        );

      case 'COMBO_BOLUS':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('modal.insulinUnits')}</span>
              <input type="number" min="0.1" max="100" step="0.1" placeholder="e.g. 3.0"
                value={comboInsulin} onChange={e => setComboInsulin(e.target.value)}
                style={inputStyle} required autoFocus />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.comboSplitNow')}</span>
              <input type="number" min="0" max="100" step="5" placeholder="e.g. 50"
                value={comboSplitNow} onChange={e => setComboSplitNow(e.target.value)}
                style={inputStyle} />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.comboDuration')}</span>
              <input type="number" min="5" max="480" step="5" placeholder="e.g. 120"
                value={comboDurationMin} onChange={e => setComboDurationMin(e.target.value)}
                style={inputStyle} />
            </label>
          </>
        );

      case 'BASAL':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('modal.insulinUnits')}</span>
              <input type="number" min="0.1" max="200" step="0.1" placeholder="e.g. 10.0"
                value={basalInsulin} onChange={e => setBasalInsulin(e.target.value)}
                style={inputStyle} required autoFocus />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.insulinType')}</span>
              <input type="text" placeholder="e.g. Lantus"
                value={basalInsulinType} onChange={e => setBasalInsulinType(e.target.value)}
                style={inputStyle} />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.durationHours')}</span>
              <input type="number" min="1" max="48" step="0.5" placeholder="e.g. 24"
                value={basalDurationHours} onChange={e => setBasalDurationHours(e.target.value)}
                style={inputStyle} />
            </label>
          </>
        );

      case 'TEMP_BASAL':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('modal.tempBasalRate')}</span>
              <input type="number" min="0" max="20" step="0.05" placeholder="e.g. 0.5"
                value={tempBasalRate} onChange={e => setTempBasalRate(e.target.value)}
                style={inputStyle} required autoFocus />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.durationMinutes')}</span>
              <input type="number" min="5" max="1440" step="5" placeholder="e.g. 30"
                value={tempBasalDurationMin} onChange={e => setTempBasalDurationMin(e.target.value)}
                style={inputStyle} required />
            </label>
            <label style={{ ...labelStyle, flexDirection: 'row', alignItems: 'center', gap: '8px', cursor: 'pointer' }} onClick={() => setTempBasalAbsolute(!tempBasalAbsolute)}>
              <input type="checkbox" checked={tempBasalAbsolute} onChange={() => setTempBasalAbsolute(!tempBasalAbsolute)} />
              <span>{t('modal.tempBasalAbsolute')}</span>
            </label>
          </>
        );

      case 'PUMP_SUSPEND':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('modal.durationMinutes')}</span>
              <input type="number" min="1" max="1440" step="1" placeholder="e.g. 30"
                value={pumpSuspendDurationMin} onChange={e => setPumpSuspendDurationMin(e.target.value)}
                style={inputStyle} required autoFocus />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.pumpSuspendReason')}</span>
              <input type="text" placeholder="e.g. exercise"
                value={pumpSuspendReason} onChange={e => setPumpSuspendReason(e.target.value)}
                style={inputStyle} />
            </label>
          </>
        );

      case 'CARBS':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('modal.carbs')}</span>
              <input type="number" min="1" max="500" step="1" placeholder="e.g. 45"
                value={carbs} onChange={e => setCarbs(e.target.value)}
                style={inputStyle} required autoFocus />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.absorptionTime')}</span>
              <input type="number" min="0.5" max="10" step="0.5" placeholder="e.g. 3"
                value={absorptionTime} onChange={e => setAbsorptionTime(e.target.value)}
                style={inputStyle} />
            </label>
          </>
        );

      case 'EXERCISE':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('modal.exerciseDuration')}</span>
              <input type="number" min="1" max="600" step="1" placeholder="e.g. 60"
                value={exerciseDuration} onChange={e => setExerciseDuration(e.target.value)}
                style={inputStyle} required autoFocus />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.exerciseIntensity')}</span>
              <select value={exerciseIntensity}
                onChange={e => setExerciseIntensity(e.target.value as 'light' | 'moderate' | 'intense')}
                style={inputStyle}>
                <option value="light">{t('modal.intensityLight')}</option>
                <option value="moderate">{t('modal.intensityModerate')}</option>
                <option value="intense">{t('modal.intensityIntense')}</option>
              </select>
            </label>
          </>
        );

      case 'NOTE':
        return (
          <label style={labelStyle}>
            <span>{t('modal.noteText')}</span>
            <textarea placeholder="e.g. felt low before dinner"
              value={noteText} onChange={e => setNoteText(e.target.value)}
              style={{ ...inputStyle, minHeight: '80px', resize: 'vertical' }}
              required autoFocus />
          </label>
        );

      case 'SITE_CHANGE':
        return (
          <label style={labelStyle}>
            <span>{t('modal.location')}</span>
            <input type="text" placeholder="e.g. left abdomen"
              value={siteLocation} onChange={e => setSiteLocation(e.target.value)}
              style={inputStyle} autoFocus />
          </label>
        );

      case 'SENSOR_INSERT':
        return (
          <label style={labelStyle}>
            <span>{t('modal.sensorModel')}</span>
            <input type="text" placeholder="e.g. Dexcom G7"
              value={sensorModel} onChange={e => setSensorModel(e.target.value)}
              style={inputStyle} autoFocus />
          </label>
        );

      case 'INSULIN_CHANGE':
        return (
          <label style={labelStyle}>
            <span>{t('modal.newInsulinType')}</span>
            <input type="text" placeholder="e.g. NovoRapid"
              value={newInsulinType} onChange={e => setNewInsulinType(e.target.value)}
              style={inputStyle} autoFocus />
          </label>
        );

      case 'ACTIVITY':
        return (
          <>
            <label style={labelStyle}>
              <span>{t('modal.activityName')}</span>
              <input type="text" placeholder="e.g. running, cycling"
                value={activityName} onChange={e => setActivityName(e.target.value)}
                style={inputStyle} required autoFocus />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.activityDuration')}</span>
              <input type="number" min="1" max="600" step="1" placeholder="e.g. 45"
                value={activityDuration} onChange={e => setActivityDuration(e.target.value)}
                style={inputStyle} required />
            </label>
            <label style={labelStyle}>
              <span>{t('modal.activityIntensity')}</span>
              <select value={activityIntensity}
                onChange={e => setActivityIntensity(e.target.value as 'low' | 'moderate' | 'high')}
                style={inputStyle}>
                <option value="low">{t('modal.intensityLight')}</option>
                <option value="moderate">{t('modal.intensityModerate')}</option>
                <option value="high">{t('modal.intensityIntense')}</option>
              </select>
            </label>
          </>
        );
    }
  };

  return (
    <div
      style={{ background: 'var(--overlay-bg)', position: 'fixed', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}
      onClick={onClose}
    >
      <div
        style={{ background: 'var(--modal-bg)', border: '1px solid var(--modal-border)', padding: '24px', borderRadius: '16px', minWidth: '380px', maxWidth: '480px', width: '90%', boxShadow: '0 25px 50px rgba(0,0,0,0.6)', maxHeight: '90vh', overflowY: 'auto' }}
        onClick={e => e.stopPropagation()}
      >
        <h2 style={{ marginTop: 0, marginBottom: '20px' }}>{t('modal.title')}</h2>
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <label style={labelStyle}>
            <span>{t('modal.type')}</span>
            <select value={type} onChange={e => setType(e.target.value as PatientTreatmentType)} style={inputStyle} required>
              {TREATMENT_TYPES.map(val => (
                <option key={val} value={val}>{t(`modal.types.${val}`)}</option>
              ))}
            </select>
          </label>

          <label style={labelStyle}>
            <span>{t('modal.treatedAt')}</span>
            <input type="datetime-local" lang={locale} value={treatedAt}
              onChange={e => setTreatedAt(e.target.value)} style={inputStyle} required />
          </label>

          {renderTypeFields()}

          <label style={labelStyle}>
            <span>{t('modal.notes')}</span>
            <input type="text" placeholder={t('modal.notesPlaceholder')}
              value={notes} onChange={e => setNotes(e.target.value)} style={inputStyle} />
          </label>

          {error && <div role="alert" className="error">{error}</div>}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '8px' }}>
            <button type="button" onClick={onClose}
              style={{ padding: '8px 16px', cursor: 'pointer', borderRadius: '4px' }}>
              {t('modal.cancel')}
            </button>
            <button type="submit"
              disabled={isSaving}
              className="primary"
              style={{ padding: '8px 16px', cursor: isSaving ? 'not-allowed' : 'pointer' }}>
              {isSaving ? t('modal.saving') : t('modal.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
