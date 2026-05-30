import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

/** Extended config that carries the one-shot retry flag for 429 handling. */
interface RetryableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

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
    const config = error.config as RetryableConfig | undefined

    if (error.response?.status === 429 && config != null && !config._retried) {
      // Respect the Retry-After header (seconds); fall back to 60 s.
      const retryAfterHeader = error.response.headers['retry-after']
      const delayMs =
        retryAfterHeader != null ? parseInt(String(retryAfterHeader), 10) * 1000 : 60_000
      const retryConfig: RetryableConfig = { ...config, _retried: true }
      return new Promise((resolve, reject) => {
        setTimeout(() => {
          axiosInstance.request(retryConfig).then(resolve).catch(reject)
        }, delayMs)
      })
    }

    if (error.response?.status === 401) {
      // useAuth() is unavailable here (outside the React tree). Dispatch a custom
      // event; App.tsx listens and calls auth.signinRedirect() to restart OIDC login.
      window.dispatchEvent(new CustomEvent('auth:unauthorized'))
    }
    return Promise.reject(error)
  },
)
