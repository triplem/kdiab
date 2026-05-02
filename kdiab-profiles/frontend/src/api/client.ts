import { DefaultApi } from './generated';
import axios from 'axios';
import { getAccessToken } from './tokenProvider';

// Create a single axios instance
const axiosInstance = axios.create({
  baseURL: '/api/v1',
});

axiosInstance.interceptors.request.use((config) => {
  if (!config.headers['X-Correlation-ID']) {
    config.headers['X-Correlation-ID'] = crypto.randomUUID();
  }

  // Token is set by App.tsx via setAccessToken() whenever the OIDC user changes.
  // This avoids reading sessionStorage directly with a hardcoded key.
  const token = getAccessToken();
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }

  return config;
});

axiosInstance.interceptors.response.use((response) => {
  const correlationId = response.headers['x-correlation-id'];
  if (correlationId) {
    console.debug(`[Response Correlation-ID: ${correlationId}]`);
  }
  return response;
});

// Export the underlying axios instance for testing the request interceptor
export { axiosInstance };

// Export the API client
export const api = new DefaultApi(undefined, '/api/v1', axiosInstance);

export const customApi = {
  acceptProposedProfile: (userId: string, profileId: string) => 
    axiosInstance.post(`/users/${userId}/profiles/${profileId}/accept`),
  rejectProposedProfile: (userId: string, profileId: string) => 
    axiosInstance.post(`/users/${userId}/profiles/${profileId}/reject`),
};
