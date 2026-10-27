import AppLink from '../components/common/AppLink';
import Icon from '../components/common/Icon';
import PageHeader from '../components/common/PageHeader';

/** Our Team page. */
export default function TeamPage() {
  return (
    <div className="bg-[#F8FAFC]">
      <PageHeader
        current="Our Team"
        eyebrow="OUR PEOPLE"
        description="Driven by purpose, collaboration and the belief that household services can be simpler."
      >
        The people behind <span className="text-[#168F5B]">NexHome.</span>
      </PageHeader>

      <div className="container max-w-6xl py-12">

        <div className="bg-white rounded-3xl p-8 sm:p-12 border border-gray-100 shadow-sm grid grid-cols-1 md:grid-cols-2 gap-10 items-center mb-12">
          <div className="w-full flex items-center justify-center">
            <div className="w-36 h-36 sm:w-44 sm:h-44 rounded-3xl bg-blue-50 text-[#0B438B] flex flex-col items-center justify-center gap-2 shadow-inner">
              <Icon name="users" size={56} />
              <span className="text-sm font-black text-[#0B315F] tracking-wide">NexHome</span>
            </div>
          </div>
          <div>
            <span className="text-xs font-bold text-[#0B438B] uppercase tracking-wider block mb-2">
              THE TEAM
            </span>
            <h2 className="text-2xl sm:text-3xl font-black text-[#0B315F] tracking-tight leading-tight mb-4">
              A shared commitment to <span className="text-[#168F5B]">better experiences.</span>
            </h2>
            <p className="text-sm text-gray-600 leading-relaxed mb-4">
              NexHome is being built by a team focused on connecting customers with the household help they need, while creating meaningful opportunities for skilled service professionals.
            </p>
            <p className="text-sm text-gray-600 leading-relaxed mb-6">
              We value thoughtful planning, clear communication and continuous improvement as our platform grows in Nuzvid and Nearby Areas.
            </p>
            <div className="p-4 rounded-xl bg-blue-50/70 border-l-4 border-[#0B438B] text-xs font-semibold text-[#0B315F] mb-6">
              Team members will update soon.
            </div>
            <AppLink
              to="/contact"
              className="bg-[#0B438B] hover:bg-[#08336a] text-white px-7 py-3 rounded-full text-sm font-bold inline-flex items-center gap-2 shadow-sm transition"
            >
              Contact NexHome <Icon name="arrow-right" size={16} />
            </AppLink>
          </div>
        </div>

        <div className="bg-white rounded-2xl p-8 border border-[#dcebf8] shadow-sm text-center">
          <div className="w-12 h-12 rounded-full bg-blue-50 text-[#0B438B] flex items-center justify-center mx-auto mb-3">
            <Icon name="users" size={24} />
          </div>
          <h3 className="text-lg font-extrabold text-[#0B315F] mb-1">
            Team members will update soon.
          </h3>
          <p className="text-xs text-gray-500 m-0">
            Our leadership and coordination team profiles are currently being finalized.
          </p>
        </div>

        {/* Team Members Grid (Commented out - will update soon)
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {TEAM_MEMBERS.map((member, idx) => (
            <div
              key={idx}
              className="bg-white rounded-2xl p-6 border border-gray-100 shadow-sm flex flex-col items-center text-center hover:shadow-md transition"
            >
              <div className="w-24 h-24 rounded-full overflow-hidden mb-4 border-2 border-[#168F5B]/20 bg-gray-100">
                <img
                  src={member.image}
                  alt={member.name}
                  className="w-full h-full object-cover"
                  onError={(e) => {
                    (e.target as HTMLElement).style.display = 'none';
                  }}
                />
              </div>
              <h3 className="text-base font-extrabold text-[#0B315F] mb-1">{member.name}</h3>
              <div className="text-xs font-bold text-[#168F5B] mb-2">{member.role}</div>
              <p className="text-xs text-gray-500 m-0 leading-relaxed">{member.desc}</p>
            </div>
          ))}
        </div>
        */}
      </div>
    </div>
  );
}
