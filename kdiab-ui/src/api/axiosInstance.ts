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
      // useAuth() is unavailable here (outside the React tree). Dispatch a custom
      // event; App.tsx listens and calls auth.signinRedirect() to restart OIDC login.
      window.dispatchEvent(new CustomEvent('auth:unauthorized'))
    }
    return Promise.reject(error)
  }
)
