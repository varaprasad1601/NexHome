import { useEffect, useSyncExternalStore } from 'react';

import { apiRequest } from '../api/client';
import { fileUrl } from '../api/files';

/**
 * The public site's service catalogue and FAQs from the backend (public endpoints, no sign-in): the active service
 * categories (GET /api/catalog/categories) with their active services (GET /api/catalog/services, every page), and
 * the published website FAQs (GET /api/faqs?audience=WEB&general=true: app FAQs shown to Web, not those of a category
 * or service). Each is loaded once per visit and shared by every page. **There is no built-in content**: when the
 * server cannot be reached (offline, CORS, an error) the pages show nothing of it, only a "could not load" notice with
 * Try again (`error`, `retry`); a failed load is not kept, so the next page that needs it asks again.
 */

/** A loaded-once value shared by every component: { loading, error, data }. */
function createStore(fetcher, empty) {
  let state = { loading: true, error: false, data: empty };
  let request = null;
  const listeners = new Set();
  const set = (next) => {
    state = next;
    listeners.forEach((listener) => listener());
  };
  const load = () => {
    if (request || (!state.loading && !state.error)) return;
    if (state.error) set({ loading: true, error: false, data: empty });
    request = fetcher()
      .then((data) => set({ loading: false, error: false, data }))
      .catch(() => set({ loading: false, error: true, data: empty }))
      .finally(() => {
        request = null;
      });
  };
  return {
    load,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    get: () => state,
    retry: () => {
      if (state.error) load();
    }
  };
}

async function fetchCatalog() {
  const [categories, services] = await Promise.all([apiRequest('/api/catalog/categories'), fetchAllServices()]);
  return categories.map((category) => ({
    categoryId: category.serviceCategoryId,
    slug: category.slug,
    name: category.name,
    description: category.description,
    imageUrl: fileUrl(category.iconUrl) ?? null,
    services: services
      .filter((service) => service.categoryId === category.serviceCategoryId)
      .map((service) => ({
        serviceId: service.serviceId,
        name: service.name,
        slug: service.slug,
        shortDescription: service.shortDescription
      }))
  }));
}

async function fetchAllServices() {
  const services = [];
  for (let page = 0; ; page += 1) {
    const result = await apiRequest(`/api/catalog/services?size=100&page=${page}`);
    services.push(...result.content);
    if (page + 1 >= result.totalPages) return services;
  }
}

async function fetchFaqs() {
  const list = await apiRequest('/api/faqs?audience=WEB&general=true');
  return list.map((faq) => ({ faqId: faq.faqId, question: faq.question, answer: faq.answer }));
}

const catalogStore = createStore(fetchCatalog, []);
const faqStore = createStore(fetchFaqs, []);

function useStore(store) {
  const state = useSyncExternalStore(store.subscribe, store.get);
  useEffect(() => {
    store.load();
  }, [store]);
  return state;
}

/**
 * { loading, error, retry, categories: [{ slug, name, description, imageUrl, services }], serviceCount }; categories is
 * empty while loading and after an error.
 */
export function useSiteCatalog() {
  const { loading, error, data } = useStore(catalogStore);
  return {
    loading,
    error,
    retry: catalogStore.retry,
    categories: data,
    serviceCount: data.reduce((total, category) => total + category.services.length, 0)
  };
}

/** { loading, error, retry, faqs: [{ faqId, question, answer }] } in their display order (empty after an error). */
export function useSiteFaqs() {
  const { loading, error, data } = useStore(faqStore);
  return { loading, error, retry: faqStore.retry, faqs: data };
}
