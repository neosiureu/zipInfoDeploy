import React, { useEffect, useState } from "react";
import "../../../css/admin/Management.css";

const MemberList = ({ initialMembers }) => {
  const [currentMembers, setCurrentMembers] = useState(
    Array.isArray(initialMembers) ? initialMembers : []
  );

  const [currentPage, setCurrentPage] = useState(1);
  const membersPerPage = 10;

  useEffect(() => {
    console.log("MemberList 초기 멤버들:", initialMembers);
    if (Array.isArray(initialMembers)) {
      setCurrentMembers(initialMembers);
      setCurrentPage(1);
    } else {
      setCurrentMembers([]);
      setCurrentPage(1);
    }
  }, [initialMembers]);

  const indexOfLastMember = currentPage * membersPerPage;
  const indexOfFirstMember = indexOfLastMember - membersPerPage;
  const currentPageMembers = currentMembers.slice(
    indexOfFirstMember,
    indexOfLastMember
  );

  const totalPages = Math.ceil(currentMembers.length / membersPerPage);

  return (
    <div className="member-list">
      <h3>회원 목록</h3>
      <table>
        <thead>
          <tr>
            <th>번호</th>
            <th>아이디</th>
            <th>권한</th>
            <th>가입일</th>
            <th>최근 로그인</th>
            <th>게시글 수</th>
            <th>차단 여부</th>
          </tr>
        </thead>
        <tbody>
          {currentMembers.length === 0 ? (
            <tr>
              <td colSpan="7" style={{ textAlign: "center" }}>
                회원 정보가 없습니다.
              </td>
            </tr>
          ) : (
            currentPageMembers.map((member) => (
              <tr key={member.memberNo /* 혹은 member.memberNumber */}>
                <td>{member.memberNo}</td>
                <td>{member.memberEmail || member.memberId}</td>
                <td>{member.memberAuth}</td>
                <td>
                  {new Date(
                    member.joinDate || member.createdAt
                  ).toLocaleDateString()}
                </td>
                <td>
                  {member.lastLoginDate
                    ? new Date(member.lastLoginDate).toLocaleDateString()
                    : "-"}
                </td>
                <td>{member.postCount ?? 0}</td>
                <td>{member.blockFlag ? "차단됨" : "정상"}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      {/* 페이지 버튼 */}
      <div className="pagination">
        {Array.from({ length: totalPages || 1 }, (_, i) => i + 1).map(
          (page) => (
            <button
              key={page}
              className={`page-button ${page === currentPage ? "active" : ""}`}
              onClick={() => setCurrentPage(page)}
            >
              {page}
            </button>
          )
        )}
      </div>
    </div>
  );
};

export default MemberList;
