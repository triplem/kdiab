import { DefaultApi } from './generated';
import axios from 'axios';
import { getAccessToken } from './tokenProvider';

const axiosInstance = axios.create({
  baseURL: (import.meta.env.VITE_API_PREFIX ?? '') + '/api/v1',
});

axiosInstance.interceptors.request.use((config) => {
  if (!config.headers['X-Correlation-ID']) {
    config.headers['X-Correlation-ID'] = crypto.randomUUID();
  }
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

export { axiosInstance };
export const api = new DefaultApi(undefined, (import.meta.env.VITE_API_PREFIX ?? '') + '/api/v1', axiosInstance);
