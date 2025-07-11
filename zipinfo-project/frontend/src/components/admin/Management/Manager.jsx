import axios from "axios";
import React, { useState, useEffect } from "react";
import { axiosAPI } from "../../../api/axiosApi";
import "../../../css/admin/Management/Manager.css";

export default function Manager() {
  // 이메일, 닉네임
  // 객체 하나로 상태 관리하는 방식
  const [form, setForm] = useState({
    email: "",
    nickname: "",
  });

  // 관리자 계정 목록 상태
  const [adminList, setAdminList] = useState([]);

  // 관리자 계정 발급
  async function createAdminAccount() {
    const { email, nickname } = form; // form 상태 안에 있는 값들 하나씩 꺼내오기

    if (email.length === 0 || nickname.length === 0) {
      alert("모든 필드를 입력해주세요!");
      return;
    }

    try {
      const response = await axiosAPI.post("/admin/createAdminAccount", {
        memberEmail: email,
        memberNickname: nickname,
      });

      if (response.status === 201) {
        const result = response.data; // 서버에서 응답해준 데이터 (body)
        alert(
          `발급된 비밀번호는 ${result} 입니다. 다시 확인할 수 없으니 저장해주시기 바랍니다.`
        );
        console.log(result);
      }

      // 입력 필드 초기화
      setForm({
        email: "",
        nickname: "",
      });

      // 관리자 계정 목록 새로고침
      loadAdminList();
    } catch (err) {
      alert(err.response.data);
      // 409일때 , 500 일때 응답받은 body 내용이 반영되어 alert 출력할 수 있게끔함.
    }
  }

  // 관리자 계정 목록 조회
  async function loadAdminList() {
    try {
      const response = await axiosAPI.get("/admin/adminList");
      setAdminList(response.data);
    } catch (err) {
      console.error("관리자 계정 목록 조회 실패:", err);
    }
  }

  // 컴포넌트 마운트 시 관리자 계정 목록 조회
  useEffect(() => {
    loadAdminList();
  }, []);

  //객체 형태 상태 변경 함수
  const handleChange = (e) => {
    const { id, value } = e.target; // 대상의 id 속성값, value값을 꺼내옴
    setForm((prev) => ({
      ...prev,
      [id]: value,
    }));
  };

  return (
    <>
      <div className="manager-div">
        <section className="manager-section">
          <h2>관리자 계정 발급</h2>
          <table>
            <tr>
              <td>사용할 이메일 : </td>
              <td>
                <input
                  id="email"
                  type="email"
                  placeholder="ex) admin2@kh.or.kr"
                  value={form.email}
                  onChange={handleChange}
                />
              </td>
            </tr>
            <tr>
              <td>사용할 이름 : </td>
              <td>
                <input
                  id="nickname"
                  type="text"
                  placeholder="ex) 관리자2"
                  value={form.nickname}
                  onChange={handleChange}
                />
              </td>
            </tr>
          </table>
          <button className="issueBtn" onClick={createAdminAccount}>
            발급
          </button>
        </section>

        <section className="manager-section">
          <h2>관리자 계정 목록</h2>
          <table className="manager-list-table" border={1}>
            <thead>
              <tr>
                <th>번호</th>
                <th>이메일</th>
                <th>관리자명</th>
                <th>가입일</th>
              </tr>
            </thead>
            <tbody>
              {adminList.map((admin, index) => (
                <tr key={admin.memberNo}>
                  <td>{admin.memberNo}</td>
                  <td>{admin.memberEmail}</td>
                  <td>{admin.memberNickname}</td>
                  <td>{admin.enrollDate}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>
    </>
  );
}
