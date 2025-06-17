import { createContext, useEffect, useState } from "react";
import { axiosAPI } from "../../api/axiosAPI";

export const MemberContext = createContext();
// Context는 React에서 컴포넌트 계층 구조(트리)를 통해 데이터를 효율적으로
// 전달하기 위한 메커니즘.
// 컴포넌트 간에 전역 상태를 공유할 수 있는 컨텍스트를 생성.

// Context는 Provider(제공자)와 Consumer(소비자) 존재
export const MemberProvider = ({ children }) => {
  // 상태값, 함수
  // 전역적으로 현재 로그인한 회원의 정보를 기억(상태)
  const [member, setMember] = useState(null);

  useEffect(() => {
    const firstMember = async () => {
      try {
        const resp = await axiosAPI.get("/member/getMember");
        if (resp.status === 200 && resp.data) {
          setMember(resp.data);
          console.log("로그인 정보가 있긴 합니다.");
        }
      } catch (error) {
        console.error("Member 불러오기 실패:", error);
      }
    };

    firstMember();
  }, []);

  return (
    <MemberContext.Provider value={{ member, setMember }}>
      {children}
    </MemberContext.Provider>
  );
};
