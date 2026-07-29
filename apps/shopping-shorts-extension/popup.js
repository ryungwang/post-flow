const captureButton = document.getElementById("capture");
const openAppButton = document.getElementById("open-app");
const statusEl = document.getElementById("status");
const summaryEl = document.getElementById("summary");
const previewEl = document.getElementById("preview");

openAppButton.addEventListener("click", async () => {
  await chrome.tabs.create({ url: "http://127.0.0.1:5173/content/shopping-shorts" });
});

captureButton.addEventListener("click", async () => {
  setStatus("현재 탭을 확인하는 중입니다.");
  captureButton.disabled = true;
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab?.id || !isCoupangUrl(tab.url)) {
      throw new Error("쿠팡 상품 페이지 탭에서 실행해 주세요.");
    }
    const [result] = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: extractCoupangProductFromDom,
    });
    const payload = result.result;
    const text = JSON.stringify(payload, null, 2);
    await navigator.clipboard.writeText(text);
    renderSummary(payload);
    previewEl.hidden = false;
    previewEl.textContent = text;
    setStatus(payload.captureWarnings.length > 0
      ? "추출 JSON을 복사했습니다. 경고 항목은 쇼핑쇼츠 화면에서 보완해 주세요."
      : "추출 JSON을 클립보드에 복사했습니다. 쇼핑쇼츠생성 화면에 붙여넣으면 됩니다.",
    payload.captureWarnings.length > 0 ? "warning" : false);
  } catch (error) {
    setStatus(error instanceof Error ? error.message : "상품 추출에 실패했습니다.", true);
  } finally {
    captureButton.disabled = false;
  }
});

function setStatus(message, state = false) {
  statusEl.textContent = message;
  statusEl.className = state === true ? "status error" : state === "warning" ? "status warning" : "status";
}

function renderSummary(payload) {
  const rows = [
    ["상품명", payload.productName || "확인 필요"],
    ["가격", payload.price ? `${payload.price.toLocaleString()}원` : "확인 필요"],
    ["이미지", `${payload.sourceImages.length}개`],
    ["특징", `${payload.features.length}개`],
    ["경고", payload.captureWarnings.length ? `${payload.captureWarnings.length}개` : "없음"],
  ];
  summaryEl.replaceChildren();
  for (const [key, value] of rows) {
    const dt = document.createElement("dt");
    const dd = document.createElement("dd");
    dt.textContent = key;
    dd.textContent = value;
    dd.title = value;
    summaryEl.append(dt, dd);
  }
  summaryEl.hidden = false;
}

function isCoupangUrl(value) {
  try {
    const url = new URL(value || "");
    return url.protocol === "https:" && (url.hostname === "coupang.com" || url.hostname.endsWith(".coupang.com"));
  } catch {
    return false;
  }
}

function extractCoupangProductFromDom() {
  const text = (value) => (value || "").replace(/\s+/g, " ").trim();
  const attr = (selector, name) => document.querySelector(selector)?.getAttribute(name) || "";
  const content = (selector) => attr(selector, "content");

  const productName = text(
    document.querySelector(".prod-buy-header__title")?.textContent
      || document.querySelector("h1")?.textContent
      || content('meta[property="og:title"]')
      || document.title.replace(" - 쿠팡!", ""),
  );

  const priceText = text(
    document.querySelector(".total-price strong")?.textContent
      || document.querySelector(".prod-coupon-price .total-price")?.textContent
      || document.querySelector(".prod-price .total-price")?.textContent
      || "",
  );
  const price = parseNumber(priceText);
  const originalPriceText = text(
    document.querySelector(".prod-origin-price")?.textContent
      || document.querySelector(".origin-price")?.textContent
      || "",
  );
  const originalPrice = parseNumber(originalPriceText);

  const brand = text(
    document.querySelector(".prod-brand-name")?.textContent
      || document.querySelector('[class*="brand"]')?.textContent
      || "",
  );

  const category = Array.from(document.querySelectorAll(".breadcrumb a, .breadcrumb span, [class*='breadcrumb'] a"))
    .map((node) => text(node.textContent))
    .filter(Boolean)
    .join(" / ");

  const options = Array.from(document.querySelectorAll(
    ".prod-option__item, .prod-option-item, [class*='option'] button, [class*='Option'] button",
  ))
    .map((node) => text(node.textContent))
    .filter((value) => value.length >= 1 && value.length <= 80)
    .filter((value, index, list) => list.indexOf(value) === index)
    .slice(0, 20);

  const descriptionCandidates = Array.from(document.querySelectorAll(
    ".prod-description, .prod-attr-item, .prod-detail-content, .product-item__table, [class*='description'], [class*='feature']",
  ))
    .map((node) => text(node.textContent))
    .filter((value) => value.length >= 8)
    .slice(0, 20);

  const features = Array.from(new Set(descriptionCandidates
    .flatMap((value) => value.split(/[·\n]/))
    .map(text)
    .filter((value) => value.length >= 4 && value.length <= 120)))
    .slice(0, 12);

  const sourceImages = Array.from(document.images)
    .filter((img) => {
      const width = Number(img.naturalWidth || img.width || 0);
      const height = Number(img.naturalHeight || img.height || 0);
      return width === 0 || height === 0 || (width >= 180 && height >= 180);
    })
    .flatMap((img) => [
      img.currentSrc,
      img.src,
      img.getAttribute("data-src"),
      img.getAttribute("data-original"),
      img.getAttribute("data-lazy-src"),
      firstSrcsetUrl(img.getAttribute("srcset")),
    ])
    .map(normalizeImageUrl)
    .filter(Boolean)
    .filter((url) => !/sprite|icon|logo|profile|avatar|blank|transparent/i.test(url))
    .filter((url) => /\.(jpg|jpeg|png|webp)(\?|$)/i.test(url) || url.includes("image"))
    .filter((url, index, list) => list.indexOf(url) === index)
    .slice(0, 30);

  const representativeImage = normalizeImageUrl(content('meta[property="og:image"]'));
  const images = representativeImage
    ? [representativeImage, ...sourceImages.filter((url) => url !== representativeImage)]
    : sourceImages;

  const captureWarnings = [];
  if (!productName) captureWarnings.push("상품명을 추출하지 못했습니다.");
  if (!price) captureWarnings.push("현재 가격을 추출하지 못했습니다.");
  if (images.length === 0) captureWarnings.push("상품 이미지를 추출하지 못했습니다.");
  if (features.length === 0 && descriptionCandidates.length === 0) captureWarnings.push("상품 특징 또는 상세 설명을 추출하지 못했습니다.");

  return {
    productName,
    brand,
    category,
    price,
    originalPrice,
    discountRate: originalPrice && price ? Math.max(0, Math.round((1 - price / originalPrice) * 100)) : null,
    options,
    features,
    description: descriptionCandidates.slice(0, 5).join("\n"),
    productUrl: location.href,
    affiliateUrl: "",
    sourceImages: images,
    extractedAt: new Date().toISOString(),
    captureWarnings,
  };

  function parseNumber(value) {
    const digits = String(value || "").replace(/[^\d]/g, "");
    return digits ? Number(digits) : null;
  }

  function normalizeImageUrl(value) {
    if (!value) return "";
    const trimmed = String(value).trim();
    const absolute = trimmed.startsWith("//")
      ? `https:${trimmed}`
      : trimmed.startsWith("/")
        ? `${location.origin}${trimmed}`
        : trimmed;
    try {
      const url = new URL(absolute);
      return ["https:", "http:"].includes(url.protocol) ? url.toString() : "";
    } catch {
      return "";
    }
  }

  function firstSrcsetUrl(value) {
    if (!value) return "";
    return value.split(",")[0]?.trim().split(/\s+/)[0] || "";
  }
}
