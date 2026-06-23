/** Lazily load the Toss Payments v1 SDK and return a TossPayments(clientKey) instance. */
let loading: Promise<void> | null = null;

function loadScript(): Promise<void> {
  if ((window as any).TossPayments) return Promise.resolve();
  if (loading) return loading;
  loading = new Promise<void>((resolve, reject) => {
    const s = document.createElement("script");
    s.src = "https://js.tosspayments.com/v1";
    s.onload = () => resolve();
    s.onerror = () => reject(new Error("Toss SDK 로드 실패"));
    document.head.appendChild(s);
  });
  return loading;
}

export async function getTossPayments(clientKey: string): Promise<any> {
  await loadScript();
  return (window as any).TossPayments(clientKey);
}
