import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useState } from 'react';
import { axiosInstance } from '../../api/client';
import { TimeframePicker, Timeframe, defaultTimeframe } from '../timeframe/TimeframePicker';
import { HbA1cCard } from './HbA1cCard';
import { TimeInRangeBar } from './TimeInRangeBar';
import { AgpChart } from './AgpChart';
import { ProfilesTable } from './ProfilesTable';

interface TirBreakdown {
  belowCount: number;
  inRangeCount: number;
  aboveCount: number;
  highCount: number;
  totalCount: number;
}

interface Hba1cResponse {
  hba1c: number | null;
  meanGlucose: number;
  readingCount: number;
  tir: TirBreakdown;
}

interface AgpHourlyData {
  hour: number;
  p10: number;
  p25: number;
  median: number;
  p75: number;
  p90: number;
  count: number;
}

interface AgpResponse {
  hourlyData: AgpHourlyData[];
}

interface ProfileSummary {
  id: string;
  status: string;
  name: string;
  createdAt?: string;
  previousProfileId?: string;
}

interface ProfilesResponse {
  profiles: ProfileSummary[];
}

interface Props {
  userId: string;
  glucoseUnit: string;
}

export function AnalyticsView({ userId, glucoseUnit }: Props) {
  const { t } = useTranslation();
  const [timeframe, setTimeframe] = useState<Timeframe>(defaultTimeframe());

  const enabled = !!userId;

  const hba1cQuery = useQuery({
    queryKey: ['hba1c', userId, timeframe.from, timeframe.to],
    queryFn: async () => {
      const res = await axiosInstance.get<Hba1cResponse>(`/users/${userId}/analytics/hba1c`, {
        params: { from: timeframe.from, to: timeframe.to },
      });
      return res.data;
    },
    enabled,
  });

  const agpQuery = useQuery({
    queryKey: ['agp', userId, timeframe.from, timeframe.to],
    queryFn: async () => {
      const res = await axiosInstance.get<AgpResponse>(`/users/${userId}/analytics/agp`, {
        params: { from: timeframe.from, to: timeframe.to },
      });
      return res.data;
    },
    enabled,
  });

  const profilesQuery = useQuery({
    queryKey: ['profiles-active', userId, timeframe.from, timeframe.to],
    queryFn: async () => {
      const res = await axiosInstance.get<ProfilesResponse>(`/users/${userId}/profiles/active`, {
        params: { from: timeframe.from, to: timeframe.to },
      });
      return res.data;
    },
    enabled,
  });

  const loading = hba1cQuery.isLoading || agpQuery.isLoading || profilesQuery.isLoading;

  return (
    <div>
      <TimeframePicker value={timeframe} onChange={setTimeframe} />

      {loading && <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>}

      {hba1cQuery.data && (
        <>
          <HbA1cCard
            hba1c={hba1cQuery.data.hba1c}
            meanGlucose={hba1cQuery.data.meanGlucose}
            tir={hba1cQuery.data.tir}
            glucoseUnit={glucoseUnit}
          />
          <TimeInRangeBar tir={hba1cQuery.data.tir} />
        </>
      )}

      {agpQuery.data && (
        <AgpChart hourlyData={agpQuery.data.hourlyData} glucoseUnit={glucoseUnit} />
      )}

      {profilesQuery.data && (
        <ProfilesTable profiles={profilesQuery.data.profiles} />
      )}
    </div>
  );
}
