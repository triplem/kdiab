import axios, { type AxiosError } from 'axios'

export const axiosInstance = axios.create()

axiosInstance.interceptors.request.use((config) => {
  if (!config.headers['X-Correlation-ID']) {
    config.headers['X-Correlation-ID'] = crypto.randomUUID()
  }
  return config
})

axiosInstance.interceptors.response.use(
  (response) => {
    const correlationId = response.headers['x-correlation-id']
    if (correlationId) {
      console.debug(`[Response Correlation-ID: ${correlationId as string}]`)
    }
    return response
  },
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      // Token expired or invalid — redirect to login so the user can re-authenticate.
      // useAuth() is unavailable here (outside the React tree), so we use a hard redirect.
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)
