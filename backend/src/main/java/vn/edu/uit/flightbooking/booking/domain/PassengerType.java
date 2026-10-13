package vn.edu.uit.flightbooking.booking.domain;

/** Loại khách theo tuổi tại ngày bay (BR-10). Em bé không chiếm ghế (BR-11). */
public enum PassengerType {

	ADULT("người lớn"),

	CHILD("trẻ em"),

	INFANT("em bé");

	private final String label;

	PassengerType(String label) {
		this.label = label;
	}

	/** Tên tiếng Việt, dùng trong câu báo lỗi. */
	public String label() {
		return label;
	}

}
