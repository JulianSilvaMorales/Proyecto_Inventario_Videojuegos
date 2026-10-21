import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { apiRequest } from './apiClient'
import { apiErrorMessage } from './errors'

interface QueryState<T> {
  key: string | null
  data: T | undefined
  error: string
}

/**
 * Consulta GET que se repite cuando cambia la ruta o se llama a `reload`.
 * Con `path` nulo no se consulta (por ejemplo, cuando falta un permiso).
 */
export function useApiQuery<T>(path: string | null) {
  const { t } = useTranslation()
  const [refresh, setRefresh] = useState(0)
  const key = path === null ? null : `${refresh}:${path}`
  const [state, setState] = useState<QueryState<T>>({
    key: null,
    data: undefined,
    error: '',
  })

  useEffect(() => {
    if (key === null || path === null) {
      return
    }
    let active = true
    apiRequest<T>(path)
      .then((data) => {
        if (active) setState({ key, data, error: '' })
      })
      .catch((reason: unknown) => {
        if (active) setState({ key, data: undefined, error: apiErrorMessage(reason, t) })
      })
    return () => {
      active = false
    }
  }, [key, path, t])

  const reload = useCallback(() => setRefresh((value) => value + 1), [])
  const current = state.key === key

  return {
    data: state.data,
    loading: key !== null && !current,
    error: current ? state.error : '',
    reload,
  }
}
