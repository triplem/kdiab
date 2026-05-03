import axios from 'axios'

export const axiosInstance = axios.create()

axiosInstance.interceptors.request.use((config) => {
  if (!config.headers['X-Correlation-ID']) {
    config.headers['X-Correlation-ID'] = crypto.randomUUID()
  }
  return config
})

axiosInstance.interceptors.response.use((response) => {
  const correlationId = response.headers['x-correlation-id']
  if (correlationId) {
    console.debug(`[Response Correlation-ID: ${correlationId as string}]`)
  }
  return response
})
