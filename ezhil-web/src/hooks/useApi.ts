import { useState, useEffect, useCallback } from 'react';
import { apiFetch } from '../services/apiClient';

export function useApiQuery<T>(path: string, params?: Record<string, string>) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = params ? `${path}?${new URLSearchParams(params)}` : path;
      const result = await apiFetch<T>(url);
      setData(result);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, JSON.stringify(params)]);

  useEffect(() => { load(); }, [load]);

  return { data, loading, error, refetch: load };
}

export function useApiMutation<TBody, TResponse>() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mutate = async (
    path: string,
    body: TBody,
    method = 'POST'
  ): Promise<TResponse | null> => {
    setLoading(true);
    setError(null);
    try {
      return await apiFetch<TResponse>(path, {
        method,
        body: JSON.stringify(body),
      });
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Unknown error');
      return null;
    } finally {
      setLoading(false);
    }
  };

  return { mutate, loading, error };
}
