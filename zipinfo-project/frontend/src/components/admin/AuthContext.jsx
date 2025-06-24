import React, { createContext, useState, useMemo, useEffect } from "react";

export const AuthContext = createContext();

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      const storedUser = localStorage.getItem("loginMember"); // ← 여기 수정
      return storedUser ? JSON.parse(storedUser) : null;
    } catch {
      return null;
    }
  });

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const isAdmin = useMemo(() => {
    return user && (user.memberAuth === 0 || user.memberAuth === "0");
  }, [user]);

  const changeInputEmail = (e) => setEmail(e.target.value);
  const changeInputPw = (e) => setPassword(e.target.value);

  const handleLogin = async (e) => {
    e.preventDefault();
    try {
      const response = await fetch("http://localhost:8080/member/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ memberEmail: email, memberPw: password }),
      });

      if (!response.ok) {
        alert("로그인 실패: 서버 응답이 정상적이지 않습니다.");
        return;
      }

      const loginUser = await response.json();

      if (loginUser.memberAuth !== 0 && loginUser.memberAuth !== "0") {
        alert("관리자 권한이 없습니다.");
        return;
      }

      setUser(loginUser);
      localStorage.setItem("loginMember", JSON.stringify(loginUser)); // ← 여기 수정

      setTimeout(() => {
        localStorage.removeItem("loginMember");
        setUser(null);
        alert("세션 만료, 다시 로그인해주세요.");
        window.location.href = "/";
      }, 3600000);
    } catch (error) {
      console.error("로그인 실패:", error);
      alert("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
  };

  const handleLogout = async () => {
    try {
      const response = await fetch("http://localhost:8080/member/logout", {
        method: "GET",
        credentials: "include",
      });
      if (response.ok) {
        localStorage.removeItem("loginMember"); // ← 여기 수정
        setUser(null);
      }
    } catch (error) {
      console.error("로그아웃 오류:", error);
    }
  };

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
}
