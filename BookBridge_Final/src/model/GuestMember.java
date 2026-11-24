package model;

public class GuestMember extends Member {
    public GuestMember(String memberId, String name) { super(memberId, name); }
    @Override public MembershipType getMembershipType() { return MembershipType.GUEST; }
}