/*sql dto에서 넘어온 DATE(string)형식을 js의 Date 객체로 변환하는 함수.*/
export const convertToJSDate = (sqlDate) => {
  const str = sqlDate; // 매개변수 ex."2025-05-13 00:00:00";
  const isoStr = str.replace(" ", "T"); // "2025-05-13T00:00:00"

  return new Date(isoStr);
};

/*매개변수 date가 현재 시간으로부터 얼마나 지났는지 대략적인 문자열로 반환하는 함수 

@param date : DATE객체

*/
export const getTimeAgo = (date) => {
  const date1 = new Date(date); // 입력된 날짜
  const date2 = new Date(); // 현재 시간

  const diffMs = date2 - date1; // 밀리초 차이
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHour = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHour / 24);

  if (diffSec < 60) {
    return "방금 전";
  } else if (diffMin < 60) {
    return `${diffMin}분 전`;
  } else if (diffHour < 24) {
    return `${diffHour}시간 전`;
  } else if (diffDay === 1) {
    return "어제";
  } else if (diffDay < 7) {
    return `${diffDay}일 전`;
  } else {
    // 7일 이상이면 yyyy-mm-dd 형식으로 보여줌
    const year = date1.getFullYear();
    const month = String(date1.getMonth() + 1).padStart(2, "0");
    const day = String(date1.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }
};
