import { useQuery } from '@tanstack/react-query'
import { profilesApi } from '../../api/profilesApi'

export function useProposedProfileCount(userId: string): number {
  const { data } = useQuery({
    queryKey: ['profiles', userId],
    queryFn: async () => {
      const response = await profilesApi.listProfiles(userId)
      return response.data.items
    },
    enabled: !!userId,
  })

  if (!data) return 0
  return data.filter((p) => p.status === 'PROPOSED').length
}
