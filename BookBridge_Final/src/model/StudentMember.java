package model;

public class StudentMember extends Member {
    public StudentMember(String memberId, String name) { super(memberId, name); }
    @Override public MembershipType getMembershipType() { return MembershipType.STUDENT; }
}