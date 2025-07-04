import React, { createContext, useState, useEffect, useRef } from "react";
import { toast } from "react-toastify";

export const AuthContext = createContext();

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  // const [loading, setLoading] = useState(true);
  const logoutTimer = useRef(null);

  useEffect(() => {
    try {
      const storedUser = localStorage.getItem("loginMember");
      if (storedUser) {
        setUser(JSON.parse(storedUser));
        startLogoutTimer();
      }
    } catch {
      setUser(null);
    } finally {
      // setLoading(false);
    }
  }, []);

  const startLogoutTimer = () => {
    if (logoutTimer.current) clearTimeout(logoutTimer.current);
    logoutTimer.current = setTimeout(() => {
      localStorage.removeItem("loginMember");
      setUser(null);
      toast.success(
        <div>
          <div className="toast-success-title">세션 만료 알림!</div>
          <div className="toast-success-body">
            세션이 만료되었습니다. 다시 로그인해주세요.
          </div>
        </div>
      );
      window.location.href = "/";
    }, 3600000);
  };

  const handleLogin = async (email, password) => {
    try {
      const response = await fetch("http://localhost:8080/member/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ memberEmail: email, memberPw: password }),
      });

      if (!response.ok) {
        toast.error(
          <div>
            <div className="toast-error-title">오류 알림!</div>
            <div className="toast-error-body">로그인 실패</div>
          </div>
        );
        return false;
      }

      const loginUser = await response.json();

      // if (loginUser.memberAuth !== 0 && loginUser.memberAuth !== "0") {
      //   alert("관리자 권한이 없습니다.");
      //   return false;
      // }

      setUser(loginUser);
      localStorage.setItem("loginMember", JSON.stringify(loginUser));
      startLogoutTimer();
      return true;
    } catch (error) {
      console.error("로그인 실패:", error);
      toast.error(
        <div>
          <div className="toast-error-title">로그인 실패 알림!</div>
          <div className="toast-error-body">
            이메일 또는 비밀번호가 올바르지 않습니다.
          </div>
        </div>
      );
      return false;
    }
  };

  const handleLogout = async () => {
    try {
      const response = await fetch("http://localhost:8080/member/logout", {
        method: "GET",
        credentials: "include",
      });
      if (response.ok) {
        localStorage.removeItem("loginMember");
        setUser(null);
        if (logoutTimer.current) clearTimeout(logoutTimer.current);
        window.location.href = "/";
      }
    } catch (error) {
      console.error("로그아웃 오류:", error);
    }
  };

  // if (loading) {
  //   return null;
  // }

  return (
    <AuthContext.Provider
      value={{
        user,
        // loading,
        setUser,
        handleLogin,
        handleLogout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
