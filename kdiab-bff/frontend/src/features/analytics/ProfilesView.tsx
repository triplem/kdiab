import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useState } from 'react';
import { axiosInstance } from '../../api/client';
import { TimeframePicker, Timeframe, defaultTimeframe } from '../timeframe/TimeframePicker';
import { ProfilesTable } from './ProfilesTable';

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
}

export function ProfilesView({ userId }: Props) {
  const { t } = useTranslation();
  const [timeframe, setTimeframe] = useState<Timeframe>(defaultTimeframe());

  const enabled = !!userId;

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

  return (
    <div>
      <TimeframePicker value={timeframe} onChange={setTimeframe} />

      {profilesQuery.isLoading && (
        <p style={{ color: 'var(--text-secondary)' }}>{t('app.loading')}</p>
      )}

      {profilesQuery.data && (
        <ProfilesTable profiles={profilesQuery.data.profiles} />
      )}

      {!profilesQuery.isLoading && !profilesQuery.data && (
        <div className="card">
          <h3>{t('analytics.profiles')}</h3>
          <p style={{ color: 'var(--text-secondary)' }}>{t('analytics.noData')}</p>
        </div>
      )}
    </div>
  );
}
