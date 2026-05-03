import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { axiosInstance } from '../../api/client';
import { TimeframePicker, Timeframe, defaultTimeframe } from '../timeframe/TimeframePicker';
import { TimelineChart } from './TimelineChart';
import { useState } from 'react';

interface Props {
  userId: string;
  glucoseUnit: string;
}

interface TimelineResponse {
  measures: Array<{
    id: string;
    measuredAt: string;
    type: string;
    data: Record<string, unknown>;
  }>;
  treatments: Array<{
    id: string;
    treatedAt: string;
    type: string;
    notes?: string;
    data: Record<string, unknown>;
  }>;
}

export function TimelineView({ userId, glucoseUnit }: Props) {
  const { t } = useTranslation();
  const [timeframe, setTimeframe] = useState<Timeframe>(defaultTimeframe());

  const { data, isLoading, error } = useQuery({
    queryKey: ['timeline', userId, timeframe.from, timeframe.to],
    queryFn: async () => {
      const res = await axiosInstance.get<TimelineResponse>(
        `/users/${userId}/timeline`,
        { params: { from: timeframe.from, to: timeframe.to } }
      );
      return res.data;
    },
    enabled: !!userId,
  });

  return (
    <div>
      <TimeframePicker value={timeframe} onChange={setTimeframe} />
      <div className="card">
        <h3>{t('timeline.title')}</h3>
        {isLoading && <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>}
        {error && <p style={{ color: 'var(--accent-danger)' }}>Error loading timeline.</p>}
        {data && data.measures.length === 0 && data.treatments.length === 0 && (
          <p style={{ color: 'var(--text-secondary)' }}>{t('timeline.noData')}</p>
        )}
        {data && (data.measures.length > 0 || data.treatments.length > 0) && (
          <TimelineChart
            measures={data.measures}
            treatments={data.treatments}
            glucoseUnit={glucoseUnit}
          />
        )}
      </div>
    </div>
  );
}
