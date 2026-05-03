import {
  ComposedChart,
  Line,
  Scatter,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ReferenceArea,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import { useTranslation } from 'react-i18next';

interface Measure {
  id: string;
  measuredAt: string;
  type: string;
  data: Record<string, unknown>;
}

interface Treatment {
  id: string;
  treatedAt: string;
  type: string;
  notes?: string;
  data: Record<string, unknown>;
}

interface Props {
  measures: Measure[];
  treatments: Treatment[];
  glucoseUnit: string;
}

const MGDL_TO_MMOL = 1 / 18.0;

function toMgDl(value: number, unit: string): number {
  return unit === 'mmol/L' ? value * 18.0 : value;
}

function displayValue(mgDl: number, unit: string): number {
  return unit === 'mmol/L' ? Math.round(mgDl * MGDL_TO_MMOL * 10) / 10 : Math.round(mgDl);
}

export function TimelineChart({ measures, treatments, glucoseUnit }: Props) {
  const { t } = useTranslation();

  const tirLow = displayValue(70, glucoseUnit);
  const tirHigh = displayValue(180, glucoseUnit);

  // Build CGM line data
  const cgmData = measures
    .filter(m => m.type === 'CGM')
    .map(m => ({
      ts: new Date(m.measuredAt).getTime(),
      value: (() => {
        const raw = m.data['value'];
        const val = typeof raw === 'number' ? raw : typeof raw === 'string' ? parseFloat(raw) : NaN;
        if (isNaN(val)) return undefined;
        const storageUnit = typeof m.data['unit'] === 'string' ? m.data['unit'] as string : 'mg/dL';
        const mgDl = toMgDl(val, storageUnit);
        return displayValue(mgDl, glucoseUnit);
      })(),
    }))
    .filter(d => d.value !== undefined)
    .sort((a, b) => a.ts - b.ts);

  // BGM dots
  const bgmData = measures
    .filter(m => m.type === 'BGM')
    .map(m => ({
      ts: new Date(m.measuredAt).getTime(),
      bgmValue: (() => {
        const raw = m.data['value'];
        const val = typeof raw === 'number' ? raw : typeof raw === 'string' ? parseFloat(raw) : NaN;
        if (isNaN(val)) return undefined;
        const storageUnit = typeof m.data['unit'] === 'string' ? m.data['unit'] as string : 'mg/dL';
        const mgDl = toMgDl(val, storageUnit);
        return displayValue(mgDl, glucoseUnit);
      })(),
    }))
    .filter(d => d.bgmValue !== undefined);

  // Treatment scatter — place at tirHigh + 10 for visibility
  const treatmentData = treatments.map(tr => ({
    ts: new Date(tr.treatedAt).getTime(),
    y: tirHigh + 10,
    type: tr.type,
    notes: tr.notes,
  }));

  const formatTs = (ts: number) =>
    new Date(ts).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });

  const yLabel = glucoseUnit === 'mmol/L' ? 'mmol/L' : 'mg/dL';

  return (
    <ResponsiveContainer width="100%" height={350}>
      <ComposedChart margin={{ top: 10, right: 20, left: 10, bottom: 10 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis
          dataKey="ts"
          type="number"
          domain={['auto', 'auto']}
          tickFormatter={formatTs}
          scale="time"
          label={{ value: '', position: 'insideBottom' }}
        />
        <YAxis
          label={{ value: yLabel, angle: -90, position: 'insideLeft', offset: 10 }}
        />
        <Tooltip
          labelFormatter={(val: number) => new Date(val).toLocaleString()}
          formatter={(val: number, name: string) => [`${val} ${yLabel}`, name]}
        />
        <Legend />

        {/* TIR target shaded band */}
        <ReferenceArea y1={tirLow} y2={tirHigh} fill="rgba(16, 185, 129, 0.07)" />
        <ReferenceLine y={tirLow} stroke="var(--accent-danger)" strokeDasharray="4 4" label={{ value: String(tirLow), fill: 'var(--accent-danger)', fontSize: 11 }} />
        <ReferenceLine y={tirHigh} stroke="var(--accent-warning)" strokeDasharray="4 4" label={{ value: String(tirHigh), fill: 'var(--accent-warning)', fontSize: 11 }} />

        {/* CGM line */}
        <Line
          data={cgmData}
          dataKey="value"
          name={t('timeline.glucose') + ' (CGM)'}
          stroke="var(--chart-cgm)"
          dot={false}
          strokeWidth={2}
          connectNulls={false}
        />

        {/* BGM dots */}
        {bgmData.length > 0 && (
          <Scatter
            data={bgmData}
            dataKey="bgmValue"
            name={t('timeline.glucose') + ' (BGM)'}
            fill="var(--chart-bgm)"
          />
        )}

        {/* Treatment events */}
        {treatmentData.length > 0 && (
          <Scatter
            data={treatmentData}
            dataKey="y"
            name={t('timeline.treatment')}
            fill="var(--chart-bolus)"
            shape="triangle"
          />
        )}
      </ComposedChart>
    </ResponsiveContainer>
  );
}
