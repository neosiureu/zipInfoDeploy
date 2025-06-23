import axios from "axios";
import { createContext, useState, useMemo, useEffect } from "react";

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    const storedUser = localStorage.getItem("userData");
    return storedUser ? JSON.parse(storedUser) : null;
  });

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  // 관리자 여부 판단
  const isAdmin = useMemo(() => {
    return user && (user.memberAuth === 0 || user.memberAuth === "0");
  }, [user]);

  // 로그인 입력 핸들러
  const changeInputEmail = (e) => setEmail(e.target.value);
  const changeInputPw = (e) => setPassword(e.target.value);

  // ✅ 로그인 처리
  const handleLogin = async (e) => {
    e.preventDefault();

    try {
      const response = await axios.post(
        "http://localhost:8080/member/login",
        {
          memberEmail: email,
          memberPw: password,
        },
        {
          withCredentials: true, // 세션 공유
        }
      );

      console.log("🔵 전체 응답:", response); // 전체 axios 응답 객체 확인
      console.log("🟢 응답 데이터:", response.data); // 실제 로그인된 사용자 정보

      const loginUser = response.data;
      loginUser.memberAuth = loginUser.authority; // 이 한 줄 추가

      if (
        !loginUser ||
        (loginUser.memberAuth !== 0 && loginUser.memberAuth !== "0")
      ) {
        alert("관리자 권한이 없습니다.");
        return;
      }

      setUser(loginUser);
      localStorage.setItem("userData", JSON.stringify(loginUser));

      // ✅ 자동 로그아웃: 1시간 후
      setTimeout(() => {
        localStorage.removeItem("userData");
        setUser(null);
        alert("세션이 만료되었습니다. 다시 로그인 해주세요.");
        window.location.href = "/";
      }, 60 * 60 * 1000);
    } catch (error) {
      console.error("❌ 로그인 실패:", error);

      if (error.response) {
        console.error("🟥 서버 응답 코드:", error.response.status);
        console.error("🟥 서버 응답 메시지:", error.response.data);
      }

      alert("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
  };

  // ✅ 로그아웃 처리
  const handleLogout = async () => {
    try {
      const response = await axios.get("http://localhost:8080/member/logout", {
        withCredentials: true,
      });

      if (response.status === 200) {
        localStorage.removeItem("userData");
        setUser(null);
      }
    } catch (error) {
      console.error("로그아웃 오류:", error);
    }
  };

  // ✅ 최초 로그인 여부 확인 (선택)
  useEffect(() => {
    console.log("현재 로그인된 사용자:", user);
  }, [user]);

  const globalState = {
    user,
    isAdmin,
    changeInputEmail,
    changeInputPw,
    handleLogin,
    handleLogout,
  };

  return (
    <AuthContext.Provider value={globalState}>{children}</AuthContext.Provider>
  );
};
