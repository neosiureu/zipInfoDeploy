import React, { createContext, useState, useEffect } from "react";

// AuthContext 생성: 전역에서 user 상태 및 로그인/로그아웃 로직 공유
export const AuthContext = createContext();

// AuthProvider 컴포넌트
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null); // 로그인된 사용자 정보
  const [loading, setLoading] = useState(true); // 초기 로딩 여부

  // 최초 렌더링 시 localStorage에서 사용자 정보 로딩
  useEffect(() => {
    try {
      const storedUser = localStorage.getItem("loginMember");
      if (storedUser) {
        setUser(JSON.parse(storedUser));
      }
    } catch {
      setUser(null);
    } finally {
      setLoading(false); // 무조건 로딩 종료
    }
  }, []);

  // 이메일/비밀번호를 받아 로그인 처리
  const handleLogin = async (email, password) => {
    try {
      const response = await fetch("http://localhost:8080/member/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include", // 쿠키 인증 시 필요
        body: JSON.stringify({ memberEmail: email, memberPw: password }),
      });

      if (!response.ok) {
        alert("로그인 실패: 서버 응답이 정상적이지 않습니다.");
        return false;
      }

      const loginUser = await response.json();
      console.log("로그인 성공 - 서버에서 받은 사용자 데이터:", loginUser);

      // 관리자 권한 필터링
      // => 관리자 전용 기능을 보호하려면 조건 걸기
      if (loginUser.memberAuth !== 0 && loginUser.memberAuth !== "0") {
        alert("관리자 권한이 없습니다.");
        return false;
      }

      // 사용자 정보 저장
      setUser(loginUser);
      localStorage.setItem("loginMember", JSON.stringify(loginUser));

      // 자동 로그아웃 타이머 (1시간 후)
      setTimeout(() => {
        localStorage.removeItem("loginMember");
        setUser(null);
        alert("세션 만료, 다시 로그인해주세요.");
        window.location.href = "/";
      }, 3600000); // 1시간 = 3600000ms

      return true; // 로그인 성공
    } catch (error) {
      console.error("로그인 실패:", error);
      alert("이메일 또는 비밀번호가 올바르지 않습니다.");
      return false;
    }
  };

  // 로그아웃 처리
  const handleLogout = async () => {
    try {
      const response = await fetch("http://localhost:8080/member/logout", {
        method: "GET",
        credentials: "include",
      });
      if (response.ok) {
        localStorage.removeItem("loginMember");
        setUser(null);
      }
    } catch (error) {
      console.error("로그아웃 오류:", error);
    }
  };

  // 디버깅용: 로그인 사용자 로그 출력
  useEffect(() => {
    console.log("현재 로그인된 사용자:", user);
  }, [user]);

  // 로딩 중엔 렌더링 차단 (선택적으로 로딩 스피너 넣어도 됨)
  if (loading) {
    return null;
  }

  // Context에 전역으로 전달할 값들
  return (
    <AuthContext.Provider
      value={{
        user, // 로그인된 사용자 정보
        setUser, // 외부에서 사용자 상태 직접 변경 가능
        handleLogin, // 로그인 함수
        handleLogout, // 로그아웃 함수
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
