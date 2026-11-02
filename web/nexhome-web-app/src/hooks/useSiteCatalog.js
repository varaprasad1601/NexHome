import { useEffect, useState } from 'react';

import { apiRequest } from '../api/client';
import { fileUrl } from '../api/files';
import { ALL_FAQS, SERVICES } from '../data/siteData';

/**
 * The public site's service catalogue and FAQs from the backend (public endpoints, no sign-in): the active service
 * categories (GET /api/catalog/categories) with their active services (GET /api/catalog/services, every page), and
 * the published website FAQs (GET /api/faqs?audience=WEB&general=true: app FAQs shown to Web, not those of a category or service). Each is
 * loaded once per visit and shared by every page. When the server cannot be reached, the site's built-in content
 * (data/siteData.js) is shown instead, so the site never goes blank.
 */

const STATIC_CATALOG = {
  source: 'static',
  categories: SERVICES.map((service) => ({
    categoryId: service.slug,
    slug: service.slug,
    name: service.name,
    description: service.fullDesc || service.shortDesc,
    imageUrl: service.image,
    services: service.options.map((name) => ({ serviceId: name, name, shortDescription: null }))
  }))
};
const STATIC_FAQS = ALL_FAQS.map((faq, index) => ({ faqId: `static-${index}`, question: faq.q, answer: faq.a }));

let catalog = null;
let catalogRequest = null;
let faqs = null;
let faqsRequest = null;

async function fetchCatalog() {
  const [categories, services] = await Promise.all([apiRequest('/api/catalog/categories'), fetchAllServices()]);
  return {
    source: 'database',
    categories: categories.map((category) => ({
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
    }))
  };
}

async function fetchAllServices() {
  const services = [];
  for (let page = 0; ; page += 1) {
    const result = await apiRequest(`/api/catalog/services?size=100&page=${page}`);
    services.push(...result.content);
    if (page + 1 >= result.totalPages) return services;
  }
}

/** { loading, source ('database' | 'static'), categories: [{ slug, name, description, imageUrl, services }], serviceCount }. */
export function useSiteCatalog() {
  const [state, setState] = useState(() => catalog ?? { loading: true, source: null, categories: [] });
  useEffect(() => {
    if (catalog) return undefined;
    let cancelled = false;
    catalogRequest ??= fetchCatalog().catch(() => STATIC_CATALOG);
    catalogRequest.then((loaded) => {
      catalog = { ...loaded, loading: false };
      if (!cancelled) setState(catalog);
    });
    return () => {
      cancelled = true;
    };
  }, []);
  return { ...state, serviceCount: state.categories.reduce((total, category) => total + category.services.length, 0) };
}

/** { loading, faqs: [{ faqId, question, answer }] } in their display order. */
export function useSiteFaqs() {
  const [state, setState] = useState(() => faqs ?? { loading: true, faqs: [] });
  useEffect(() => {
    if (faqs) return undefined;
    let cancelled = false;
    faqsRequest ??= apiRequest('/api/faqs?audience=WEB&general=true')
      .then((list) => list.map((faq) => ({ faqId: faq.faqId, question: faq.question, answer: faq.answer })))
      .catch(() => STATIC_FAQS);
    faqsRequest.then((loaded) => {
      faqs = { loading: false, faqs: loaded };
      if (!cancelled) setState(faqs);
    });
    return () => {
      cancelled = true;
    };
  }, []);
  return state;
}
