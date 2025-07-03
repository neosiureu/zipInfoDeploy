import { createContext, useEffect, useState } from "react";
import { axiosAPI } from "../../api/axiosApi";
import { jwtDecode } from "jwt-decode";
import { useLocation } from "react-router-dom";

export const MemberContext = createContext();

export const MemberProvider = ({ children }) => {
  /* 1) 스토리지와 state 동기화 */
  const [member, setMember] = useState(() => {
    const raw = localStorage.getItem("loginMember");
    return raw && raw !== "undefined" ? JSON.parse(raw) : null;
  });

  const token = localStorage.getItem("accessToken");
  const location = useLocation();
  const skipFetchNow =
    location.pathname.startsWith("/login") ||
    location.pathname.startsWith("/oauth2/kakao");

  useEffect(() => {
    if (!member && token) {
      try {
        const { sub, email, loginTp, auth } = jwtDecode(token);
        setMember({
          memberNo: Number(sub),
          memberEmail: email,
          memberLogin: loginTp,
          memberAuth: auth,
        });
      } catch {
        /* 디코딩 실패 = 토큰 폐기 */
        localStorage.removeItem("accessToken");
      }
    }
  }, [token, member]);

  /* 3) 서버에서 새로운 정보를 한 번만 가져오기 */
  useEffect(() => {
    if (skipFetchNow || !token) return;
    axiosAPI
      .get("/member/getMember")
      .then(({ data }) => {
        if (data) {
          // data 가 null 이면 여기 안 들어옴 – 정상
          localStorage.setItem("loginMember", JSON.stringify(data));
          setMember(data);
        }
      })
      .catch(() => {
        /* 토큰이 죽었으면 정리 */
        localStorage.removeItem("accessToken");
        localStorage.removeItem("loginMember");
        setMember(null);
      });
  }, [skipFetchNow, token]);

  return (
    <MemberContext.Provider value={{ member, setMember }}>
      {children}
    </MemberContext.Provider>
  );
};
