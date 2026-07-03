const BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export type PublicCta = {
  slug: string;
  headline: string;
  label: string;
  destinationUrl: string;
};

/** Public (no-auth) calls for the hosted lead landing page. */
export const publicApi = {
  cta: async (slug: string): Promise<PublicCta> => {
    const res = await fetch(`${BASE}/public/cta-links/${slug}`);
    if (!res.ok) throw new Error("not found");
    return res.json();
  },
  submitLead: async (slug: string, email: string, name?: string): Promise<{ destinationUrl: string }> => {
    const res = await fetch(`${BASE}/public/leads/${slug}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, name }),
    });
    if (!res.ok) throw new Error("failed");
    return res.json();
  },
};
