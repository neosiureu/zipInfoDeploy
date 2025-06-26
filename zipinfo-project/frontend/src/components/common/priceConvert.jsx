/*이하 Salepage에서 훔쳐온 코드*/

export const formatPrice = (price) => {
  if (!price || isNaN(price)) return "";

  const num = Number(price);
  const billion = Math.floor(num / 100000000);
  const million = Math.floor((num % 100000000) / 10000);

  if (billion > 0 && million > 0) return `${billion}억 ${million}`;
  if (billion > 0) return `${billion}억`;
  if (million > 0) return `${million}`;
  return num.toLocaleString();
};

// 날짜 데이터 변환 함수
export const formatDate = (dateString) => {
  if (!dateString) return "";
  const date = new Date(dateString);
  return date.toISOString().slice(0, 10); // 'YYYY-MM-DD'
};
